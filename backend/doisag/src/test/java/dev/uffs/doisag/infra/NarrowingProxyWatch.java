package dev.uffs.doisag.infra;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicInteger;

// conta os avisos "narrowing proxy" do hibernate (issue 39): eles aparecem qnd um usuario
// carregado como Users eh pedido como Prescriber na mesma sessao, e a subida com o seed
// era onde mais apareciam
//
// eh configuracao de teste pq o contador precisa ser ligado dps do boot preparar o log
// (q apaga o q foi ligado antes) e antes do seed rodar. o contador zera a cada contexto
// q o liga, entao o aviso de outro teste da mesma jvm n cai na conta deste
@TestConfiguration
public class NarrowingProxyWatch {

    private static final String COUNTER_NAME = "narrowingProxyCounter";
    private static final AtomicInteger WARNINGS = new AtomicInteger();

    @Bean
    Appender<ILoggingEvent> narrowingProxyCounter() {
        Logger root = rootLogger();
        WARNINGS.set(0);
        if (root.getAppender(COUNTER_NAME) != null) {
            return root.getAppender(COUNTER_NAME);
        }
        AppenderBase<ILoggingEvent> counter = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (event.getFormattedMessage().contains("Narrowing proxy")) {
                    WARNINGS.incrementAndGet();
                }
            }
        };
        counter.setName(COUNTER_NAME);
        counter.setContext(root.getLoggerContext());
        counter.start();
        root.addAppender(counter);
        return counter;
    }

    // se o log foi recriado no meio da suite o contador some junto, e ai zero n provaria nada
    public static int warnings() {
        if (rootLogger().getAppender(COUNTER_NAME) == null) {
            throw new IllegalStateException("o contador de narrowing proxy n esta mais ligado no log");
        }
        return WARNINGS.get();
    }

    // na raiz, e n na categoria do hibernate, pra n depender do nome q cada versao usa
    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
