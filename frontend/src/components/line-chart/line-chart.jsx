import {useLayoutEffect, useRef, useState} from "react";
import {formatCutoff, valueWithBand} from "../../utils/progress-reading.js";
import {
    linePath,
    nearestIndex,
    stackLabels,
    xLabelIndexes,
    xOf,
    yOf,
    yRange,
    yTicks,
} from "../../utils/chart-scale.js";
import styles from "./line-chart.module.css";

const HEIGHT = 288;
const LEFT = 40;
const RIGHT = 20;
const BOTTOM = 28;
const LINE_HEIGHT = 14;
const DEFAULT_WIDTH = 640;
const DATE_GAP = 44;
const DOSE_NAME = "Gotas no dia";

// o escore vai com a faixa do lado como em todo lugar q ele aparece RN14
function scoreText(row) {
    return row.faixa ? valueWithBand(row.valor, row.faixa) : row.valor;
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
export default function LineChart({rows, minValue, maxValue, valueName, showDose, bandLines, cutoff, appointmentMarks}) {
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

    const values = [];
    for (const row of rows) {
        values.push(row.valor);
        if (showDose) {
            values.push(row.gotas);
        }
    }
    const range = yRange(minValue, maxValue, values);

    const plotWidth = Math.max(40, width - LEFT - RIGHT);
    const right = LEFT + plotWidth;
    const xAt = (index) => xOf(index, rows.length, LEFT, plotWidth);

    const marks = [];
    for (const mark of appointmentMarks) {
        const index = rows.findIndex((row) => row.label === mark.label);
        if (index === -1) {
            continue;
        }
        const text = mark.geraPrescricao ? "consulta + receita" : "consulta";
        // uns 6px e meio por letra no texto de 12px e o nome n passa da borda
        const halfText = text.length * 3.3;
        const textX = Math.min(Math.max(xAt(index), halfText + 2), width - halfText - 2);
        marks.push({id: mark.id, index: index, text: text, textX: textX, left: textX - halfText, right: textX + halfText});
    }
    marks.sort((first, second) => first.index - second.index);

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
    const isInside = (value) => value >= range.bottom && value <= range.top;

    const scorePoints = [];
    const dosePoints = [];
    rows.forEach((row, index) => {
        scorePoints.push({x: xAt(index), y: row.valor === null ? null : yAt(row.valor)});
        if (showDose) {
            dosePoints.push({x: xAt(index), y: row.gotas === null ? null : yAt(row.gotas)});
        }
    });

    const pickNearest = (event) => {
        const box = event.currentTarget.getBoundingClientRect();
        const scale = box.width > 0 ? width / box.width : 1;
        const index = nearestIndex((event.clientX - box.left) * scale, rows.length, LEFT, plotWidth);
        if (index !== -1) {
            setActiveIndex(index);
        }
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
    const activeX = activeRow ? xAt(activeIndex) : 0;
    const hasActiveDose = Boolean(activeRow && showDose && activeRow.gotas !== null);

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
                        {xLabelIndexes(rows.length, plotWidth, DATE_GAP).map((index) => (
                            <g key={"x" + index}>
                                <line className={styles.grid} x1={xAt(index)} x2={xAt(index)} y1={top} y2={bottom}/>
                                <line className={styles.axis} x1={xAt(index)} x2={xAt(index)} y1={bottom} y2={bottom + 5}/>
                                <text className={styles.tick} x={xAt(index)} y={bottom + 19} textAnchor="middle">
                                    {rows[index].label}
                                </text>
                            </g>
                        ))}
                        <line className={styles.axis} x1={LEFT} x2={right} y1={bottom} y2={bottom}/>
                        <line className={styles.axis} x1={LEFT} x2={LEFT} y1={top} y2={bottom}/>

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
                                x1={xAt(mark.index)}
                                x2={xAt(mark.index)}
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

                        <path className={styles.scoreLine} d={linePath(scorePoints)}/>
                        {scorePoints.map((point, index) => point.y !== null && (
                            <circle
                                key={"s" + index}
                                className={index === activeIndex ? styles.activeScoreDot : styles.scoreDot}
                                cx={point.x}
                                cy={point.y}
                                r={index === activeIndex ? 6 : 4}
                            />
                        ))}

                        {showDose && <path className={styles.doseLine} d={linePath(dosePoints)}/>}
                        {dosePoints.map((point, index) => point.y !== null && (
                            <circle
                                key={"d" + index}
                                className={index === activeIndex ? styles.activeDoseDot : styles.doseDot}
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
                                    {DOSE_NAME + " : " + activeRow.gotas}
                                </p>
                            )}
                        </div>
                    )}
                </div>
            </div>

            <ul className={styles.legend}>
                <li><span className={styles.scoreSwatch} aria-hidden="true"/>{valueName}</li>
                {showDose && <li><span className={styles.doseSwatch} aria-hidden="true"/>{DOSE_NAME}</li>}
            </ul>
        </div>
    );
}
