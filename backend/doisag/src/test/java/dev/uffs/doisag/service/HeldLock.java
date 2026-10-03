package dev.uffs.doisag.service;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// roda um fluxo numa thread enquanto outra transacao segura uma trava do banco (issues 77 e 78)
//
// a transacao de fora pega a trava e fica esperando. o fluxo eh disparado nesse meio tempo
// e precisa ficar preso nela. antes de soltar, a transacao de fora grava o registro q disputa
// com o fluxo, entao qnd ele anda de novo ja encontra esse registro no banco
// sem a trava o fluxo n espera, le o banco ainda vazio e grava o registro repetido
class HeldLock {

    // quanto o fluxo fica preso antes do teste concluir q ele esperou mesmo
    private static final long HELD_MILLIS = 300;

    // o q o fluxo devolveu, ou o erro q ele deu
    record Outcome<T>(T value, Throwable error) {}

    private final TransactionTemplate transactionTemplate;
    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    HeldLock(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    // falha se o fluxo n esperou a trava, mas so dps das duas threads terminarem,
    // pra limpeza do teste n correr junto com a gravacao de fora
    <T> Outcome<T> run(Runnable takeLock, Runnable saveBeforeReleasing, Callable<T> flow) throws Exception {
        CountDownLatch lockTaken = new CountDownLatch(1);
        CountDownLatch mayRelease = new CountDownLatch(1);
        Future<?> holder = threads.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            takeLock.run();
            lockTaken.countDown();
            await(mayRelease);
            saveBeforeReleasing.run();
        }));
        if (!lockTaken.await(5, TimeUnit.SECONDS)) {
            // o erro de verdade esta na thread de fora, entao ele sobe daqui
            holder.get(1, TimeUnit.SECONDS);
            throw new IllegalStateException("a transacao de fora n pegou a trava");
        }

        Future<T> contender = threads.submit(flow);
        boolean waited = !finishedWithin(contender, HELD_MILLIS);
        mayRelease.countDown();
        holder.get(5, TimeUnit.SECONDS);
        Outcome<T> outcome;
        try {
            outcome = new Outcome<>(contender.get(5, TimeUnit.SECONDS), null);
        } catch (ExecutionException error) {
            outcome = new Outcome<>(null, error.getCause());
        }
        if (!waited) {
            throw new AssertionError("o fluxo n esperou a trava: " + whatHappened(outcome));
        }
        return outcome;
    }

    void close() throws InterruptedException {
        threads.shutdownNow();
        threads.awaitTermination(5, TimeUnit.SECONDS);
    }

    private boolean finishedWithin(Future<?> future, long millis) throws InterruptedException {
        try {
            future.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException stillRunning) {
            return false;
        } catch (ExecutionException finishedWithError) {
            return true;
        }
    }

    // interrompida, a transacao de fora desfaz tudo em vez de gravar com o teste ja limpando
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("ninguem mandou soltar a trava");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("a transacao de fora foi interrompida", interrupted);
        }
    }

    private String whatHappened(Outcome<?> outcome) {
        return outcome.error() == null ? "terminou com " + outcome.value() : "deu " + outcome.error();
    }
}
