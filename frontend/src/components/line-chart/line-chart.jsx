import {useLayoutEffect, useRef, useState} from "react";
import {DOSE_NAME, doseText, formatCutoff, scoreText} from "../../utils/progress-reading.js";
import {
    dayNumber,
    linePath,
    nearestIndex,
    stackLabels,
    xLabelIndexes,
    xOfDay,
    yOf,
    yRange,
    yTicks,
} from "../../utils/chart-scale.js";
import styles from "./line-chart.module.css";

const HEIGHT = 288;
const LEFT = 40;
const RIGHT = 20;
// com as gotas a direita ganha o eixo delas, com numero de ate 3 algarismos
const RIGHT_WITH_DOSE = 36;
const BOTTOM = 28;
const LINE_HEIGHT = 14;
const DEFAULT_WIDTH = 640;
const DATE_GAP = 44;
// a data com o ano tem 8 letras em vez de 5 e precisa de mais espaco
const DATE_WITH_YEAR_GAP = 64;
const DOSE_AXIS_NAME = "Gotas no dia (eixo da direita)";
const PARTIAL_DOSE_NAME = "Dia com uma das doses em branco";

function doseDotClass(row, isActive) {
    if (row.partialDoseNote) {
        return isActive ? styles.activePartialDoseDot : styles.partialDoseDot;
    }
    return isActive ? styles.activeDoseDot : styles.doseDot;
}

function summaryOf(rows, valueName) {
    if (rows.length === 0) {
        return "Gráfico de " + valueName + " sem registros.";
    }
    const first = rows[0].label;
    const last = rows[rows.length - 1].label;
    const days = rows.length === 1 ? "1 registro, em " + first : rows.length + " registros, de " + first + " a " + last;
    return "Gráfico de " + valueName + " com " + days + ". Use as setas para ler cada ponto.";
}

// grafico de evolucao em svg com as cores do css entao o tema escuro vale sozinho
// o eixo x vai pela data e a linha corta onde passou mais dias q a frequencia da escala sem resposta
// (RF27, revisao das regras R3)
export default function LineChart({
    rows,
    minValue,
    maxValue,
    valueName,
    showDose,
    bandLines,
    cutoff,
    appointmentMarks,
    frequencyDays,
}) {
    const boxRef = useRef(null);
    const [width, setWidth] = useState(DEFAULT_WIDTH);
    const [activeIndex, setActiveIndex] = useState(null);

    // o desenho segue a largura do cartao pro texto ter 12px de vdd no celular e no pc
    useLayoutEffect(() => {
        const box = boxRef.current;
        // mede antes de pintar pro primeiro quadro n sair encolhido
        const firstWidth = Math.round(box.getBoundingClientRect().width);
        if (firstWidth > 0) {
            setWidth(firstWidth);
        }
        // o jsdom dos testes n tem ResizeObserver e fica na largura padrao
        if (typeof ResizeObserver !== "function") {
            return;
        }
        const observer = new ResizeObserver((entries) => {
            const newWidth = Math.round(entries[0].contentRect.width);
            if (newWidth > 0) {
                setWidth(newWidth);
            }
        });
        observer.observe(box);
        return () => observer.disconnect();
    }, []);

    // as gotas tem o proprio eixo, na direita, pq 100 gotas no eixo da nota de 0 a 10 achatavam a curva
    // do sintoma (revisao das regras R13)
    const range = yRange(minValue, maxValue, rows.map((row) => row.valor));
    const doseRange = yRange(0, null, showDose ? rows.map((row) => row.gotas) : []);

    const plotWidth = Math.max(40, width - LEFT - (showDose ? RIGHT_WITH_DOSE : RIGHT));
    const right = LEFT + plotWidth;

    // o eixo vai da primeira a ultima resposta e a consulta em dia sem resposta cai no dia dela. a consulta
    // de antes ou de dps fica so na lista, senao esticava o eixo e espremia as respostas num canto
    // a data inteira pq no todo o tempo o 18/09 de um ano n eh o do outro
    const days = rows.map((row) => dayNumber(row.date));
    const firstDay = Math.min(...days);
    const lastDay = Math.max(...days);
    const xAtDay = (day) => xOfDay(day, firstDay, lastDay, LEFT, plotWidth);
    const xs = days.map(xAtDay);

    const dateGap = rows.length > 0 && rows[0].label.length > 5 ? DATE_WITH_YEAR_GAP : DATE_GAP;
    // a ultima data fica colada na borda direita e a com o ano passaria dela
    const dateX = (index) => Math.min(xs[index], width - rows[index].label.length * 3.3 - 2);

    const marks = [];
    for (const mark of appointmentMarks) {
        const markDay = dayNumber(mark.date);
        if (markDay < firstDay || markDay > lastDay) {
            continue;
        }
        const x = xAtDay(markDay);
        const text = mark.geraPrescricao ? "consulta + receita" : "consulta";
        // uns 6px e meio por letra no texto de 12px e o nome n passa da borda
        const halfText = text.length * 3.3;
        const textX = Math.min(Math.max(x, halfText + 2), width - halfText - 2);
        marks.push({id: mark.id, x: x, text: text, textX: textX, left: textX - halfText, right: textX + halfText});
    }
    marks.sort((first, second) => first.x - second.x);

    // nome de consulta q encostaria no outro sobe uma linha
    const markLines = stackLabels(marks, 8, 3);
    let lineCount = 1;
    for (const line of markLines) {
        lineCount = Math.max(lineCount, line + 1);
    }

    const top = 10 + LINE_HEIGHT * lineCount;
    const bottom = HEIGHT - BOTTOM;
    const plotHeight = bottom - top;
    const yAt = (value) => yOf(value, range, top, plotHeight);
    const yAtDose = (value) => yOf(value, doseRange, top, plotHeight);
    const isInside = (value) => value >= range.bottom && value <= range.top;

    const scorePoints = [];
    const dosePoints = [];
    rows.forEach((row, index) => {
        scorePoints.push({x: xs[index], y: row.valor === null ? null : yAt(row.valor), day: days[index]});
        if (showDose) {
            dosePoints.push({x: xs[index], y: row.gotas === null ? null : yAtDose(row.gotas), day: days[index]});
        }
    });

    const pickNearest = (event) => {
        const box = event.currentTarget.getBoundingClientRect();
        const scale = box.width > 0 ? width / box.width : 1;
        const scaleY = box.height > 0 ? HEIGHT / box.height : 1;
        const pointerY = (event.clientY - box.top) * scaleY;
        let index = nearestIndex((event.clientX - box.left) * scale, xs);
        if (index === -1) {
            return;
        }
        // dois MEEM do msm dia ficam no msm x, ai vale o ponto mais perto na altura, senao o segundo
        // nunca abria com o mouse nem com o dedo
        const sameX = xs[index];
        for (let other = 0; other < xs.length; other++) {
            const otherY = scorePoints[other].y;
            if (xs[other] !== sameX || otherY === null) {
                continue;
            }
            const y = scorePoints[index].y;
            if (y === null || Math.abs(otherY - pointerY) < Math.abs(y - pointerY)) {
                index = other;
            }
        }
        setActiveIndex(index);
    };

    // no toque o tooltip fica dps de tirar o dedo e some ao tocar fora
    const leave = (event) => {
        if (event.pointerType !== "touch") {
            setActiveIndex(null);
        }
    };

    // arrastar o dedo de lado n da foco sozinho e sem foco o toque fora n esconde o tooltip
    const focusAfterTouch = (event) => {
        if (event.pointerType === "touch") {
            event.currentTarget.focus({preventScroll: true, focusVisible: false});
        }
    };

    // qnd o navegador pega o gesto pra rolar a tela ou dar zoom o tooltip some
    const cancelTouch = () => {
        setActiveIndex(null);
    };

    // as setas andam de ponto em ponto
    const moveWithKeys = (event) => {
        const last = rows.length - 1;
        if (last < 0) {
            return;
        }
        let next;
        if (event.key === "ArrowRight") {
            next = activeIndex === null ? 0 : Math.min(last, activeIndex + 1);
        } else if (event.key === "ArrowLeft") {
            next = activeIndex === null ? last : Math.max(0, activeIndex - 1);
        } else if (event.key === "Home") {
            next = 0;
        } else if (event.key === "End") {
            next = last;
        } else {
            return;
        }
        event.preventDefault();
        setActiveIndex(next);
    };

    const activeRow = activeIndex !== null && activeIndex < rows.length ? rows[activeIndex] : null;
    const activeX = activeRow ? xs[activeIndex] : 0;
    const hasActiveDose = Boolean(activeRow && showDose && activeRow.gotas !== null);
    const hasPartialDose = showDose && rows.some((row) => row.gotas !== null && row.partialDoseNote);

    // o tooltip fica do lado do ponto q tem mais espaco
    let tooltipStyle = {top: top, left: activeX + 12, maxWidth: Math.min(288, width - activeX - 16)};
    if (activeX >= width / 2) {
        tooltipStyle = {top: top, right: width - activeX + 12, maxWidth: Math.min(288, activeX - 16)};
    }

    return (
        <div ref={boxRef} className={styles.chart}>
            <div className={styles.plot}>
                {/* application faz o leitor de tela passar as setas pro grafico */}
                <svg
                    className={styles.svg}
                    width="100%"
                    height={HEIGHT}
                    viewBox={"0 0 " + width + " " + HEIGHT}
                    role="application"
                    aria-roledescription="gráfico"
                    aria-label={summaryOf(rows, valueName)}
                    tabIndex={0}
                    onKeyDown={moveWithKeys}
                    onBlur={() => setActiveIndex(null)}
                    onPointerDown={pickNearest}
                    onPointerMove={pickNearest}
                    onPointerUp={focusAfterTouch}
                    onPointerCancel={cancelTouch}
                    onPointerLeave={leave}
                >
                    <g aria-hidden="true">
                        {yTicks(range).map((tick) => (
                            <g key={"y" + tick}>
                                <line className={styles.grid} x1={LEFT} x2={right} y1={yAt(tick)} y2={yAt(tick)}/>
                                <text className={styles.tick} x={LEFT - 8} y={yAt(tick) + 4} textAnchor="end">{tick}</text>
                            </g>
                        ))}
                        {xLabelIndexes(xs, dateGap).map((index) => (
                            <g key={"x" + index}>
                                <line className={styles.grid} x1={xs[index]} x2={xs[index]} y1={top} y2={bottom}/>
                                <line className={styles.axis} x1={xs[index]} x2={xs[index]} y1={bottom} y2={bottom + 5}/>
                                <text className={styles.tick} x={dateX(index)} y={bottom + 19} textAnchor="middle">
                                    {rows[index].label}
                                </text>
                            </g>
                        ))}
                        <line className={styles.axis} x1={LEFT} x2={right} y1={bottom} y2={bottom}/>
                        <line className={styles.axis} x1={LEFT} x2={LEFT} y1={top} y2={bottom}/>

                        {/* o eixo das gotas fica na direita, com os numeros na cor da linha delas */}
                        {showDose && <line className={styles.axis} x1={right} x2={right} y1={top} y2={bottom}/>}
                        {showDose && yTicks(doseRange).map((tick) => (
                            <text
                                key={"d" + tick}
                                className={styles.doseTick}
                                x={right + 6}
                                y={yAtDose(tick) + 4}
                                textAnchor="start"
                            >
                                {tick}
                            </text>
                        ))}

                        {bandLines.filter((band) => isInside(band.minScore)).map((band) => (
                            <g key={band.label}>
                                <line className={styles.band} x1={LEFT} x2={right} y1={yAt(band.minScore)} y2={yAt(band.minScore)}/>
                                <text className={styles.label} x={right - 5} y={yAt(band.minScore) - 5} textAnchor="end">
                                    {band.label}
                                </text>
                            </g>
                        ))}

                        {cutoff !== null && isInside(cutoff) && (
                            <g>
                                <line className={styles.cutoff} x1={LEFT} x2={right} y1={yAt(cutoff)} y2={yAt(cutoff)}/>
                                <text className={styles.label} x={right - 5} y={yAt(cutoff) + 16} textAnchor="end">
                                    {"Corte " + formatCutoff(cutoff)}
                                </text>
                            </g>
                        )}

                        {/* cada consulta vira uma linha q sobe ate o nome dela e os nomes vem dps pro contorno passar por cima */}
                        {marks.map((mark, position) => (
                            <line
                                key={"l" + mark.id}
                                className={styles.appointment}
                                x1={mark.x}
                                x2={mark.x}
                                y1={top - 3 - LINE_HEIGHT * markLines[position]}
                                y2={bottom}
                            />
                        ))}
                        {marks.map((mark, position) => (
                            <text
                                key={"t" + mark.id}
                                className={styles.appointmentText}
                                x={mark.textX}
                                y={top - 6 - LINE_HEIGHT * markLines[position]}
                                textAnchor="middle"
                            >
                                {mark.text}
                            </text>
                        ))}

                        {activeRow && <line className={styles.cursor} x1={activeX} x2={activeX} y1={top} y2={bottom}/>}

                        <path className={styles.scoreLine} d={linePath(scorePoints, frequencyDays)}/>
                        {scorePoints.map((point, index) => point.y !== null && (
                            <circle
                                key={"s" + index}
                                className={index === activeIndex ? styles.activeScoreDot : styles.scoreDot}
                                cx={point.x}
                                cy={point.y}
                                r={index === activeIndex ? 6 : 4}
                            />
                        ))}

                        {/* as gotas sao da ficha, entao a frequencia eh a msm do escore */}
                        {showDose && <path className={styles.doseLine} d={linePath(dosePoints, frequencyDays)}/>}
                        {dosePoints.map((point, index) => point.y !== null && (
                            <circle
                                key={"d" + index}
                                className={doseDotClass(rows[index], index === activeIndex)}
                                cx={point.x}
                                cy={point.y}
                                r={index === activeIndex ? 5 : 3}
                            />
                        ))}
                    </g>
                </svg>

                <div role="status">
                    {activeRow && (
                        <div className={styles.tooltip} style={tooltipStyle}>
                            <p className={styles.tooltipLabel}>{activeRow.label}</p>
                            <p className={styles.tooltipItem}>
                                <span className={styles.scoreSwatch} aria-hidden="true"/>
                                {valueName + " : " + scoreText(activeRow)}
                            </p>
                            {hasActiveDose && (
                                <p className={styles.tooltipItem}>
                                    <span className={styles.doseSwatch} aria-hidden="true"/>
                                    {DOSE_NAME + " : " + doseText(activeRow)}
                                </p>
                            )}
                        </div>
                    )}
                </div>
            </div>

            <ul className={styles.legend}>
                <li><span className={styles.scoreSwatch} aria-hidden="true"/>{valueName}</li>
                {showDose && <li><span className={styles.doseSwatch} aria-hidden="true"/>{DOSE_AXIS_NAME}</li>}
                {hasPartialDose && (
                    <li><span className={styles.partialDoseSwatch} aria-hidden="true"/>{PARTIAL_DOSE_NAME}</li>
                )}
            </ul>
        </div>
    );
}
