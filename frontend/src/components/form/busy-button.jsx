// botao de salvar ou enviar q segura o foco enquanto a api responde (CT-39)
// com disabled o navegador tirava o foco do botao e ele ia pro body, sumindo da tela de quem usa o teclado.
// ocupado ele fica com aria-disabled, q o leitor de tela anuncia, e o clique repetido eh ignorado,
// inclusive o enter num campo, q o navegador transforma em clique no botao de enviar
export default function BusyButton({isBusy, busyText, onClick, children, ...buttonProps}) {
    const handleClick = (event) => {
        if (isBusy) {
            event.preventDefault();
            return;
        }
        if (onClick) {
            onClick(event);
        }
    };

    return (
        <button {...buttonProps} aria-disabled={isBusy ? "true" : undefined} onClick={handleClick}>
            {isBusy && busyText ? busyText : children}
        </button>
    );
}
