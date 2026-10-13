# Truco do Pato 🦆🕶️

Marcador de pontos de truco pra celular, com um pato de óculos escuros (e cordão com o zap) que zoa o adversário.

## O que tem

- **Placar dos dois times** até 12 tentos, com palitos de fósforo acendendo a cada ponto.
- **Botões de truco**: `+1`, `TRUCO +3`, `SEIS +6`, `NOVE +9`, `DOZE +12` e `−1` pra corrigir.
- **TRUCO! na tela inteira**, com tremida, vibração e o narrador do celular gritando "truco", "seis", "nove", "doze".
- **O pato**:
  - toca no pato e ele vira pro time que tá perdendo e solta um "QUÁ!" com uma zoeira;
  - os botões **◀ zoar** e **zoar ▶** escolhem quem vai ser zoado;
  - segura o pato pra **zoeira máxima** (QUÁ QUÁ QUÁ em sequência);
  - quando alguém leva truco, o pato zoa sozinho.
- **Mão de onze** e **mão de ferro** sinalizadas no placar.
- **Partidas ganhas** de cada time e tela de vitória ("Fulano vai pagar o pato").
- **Desfazer** qualquer jogada, inclusive a que deu vitória.
- Nome dos times editável (toca no nome).
- Tudo fica salvo no aparelho: se fechar o app, o placar continua lá.
- Mantém a tela acesa durante o jogo (quando o navegador deixa).
- Cola das regras do truco paulista no menu.

O som do pato é gerado na hora pelo próprio navegador (Web Audio), sem arquivo de áudio.

## Como usar no celular

O app é um arquivo `index.html` só, sem dependências. Pra jogar:

1. Publique a pasta com o GitHub Pages: no repositório, **Settings → Pages → Deploy from a branch**, escolha a branch e a pasta raiz (`/`). O app fica em
   `https://<usuario>.github.io/<repositorio>/truco-do-pato/`.
2. Abra esse link no celular.
3. Adicione à tela inicial:
   - **iPhone (Safari):** botão de compartilhar → *Adicionar à Tela de Início*.
   - **Android (Chrome):** menu ⋮ → *Instalar app* ou *Adicionar à tela inicial*.

Depois de instalado, ele abre em tela cheia e funciona sem internet.

> No iPhone, se o pato não fizer barulho, confira o volume de mídia. Em iOS recentes o som toca mesmo com a chave de silencioso ligada.

## Rodar no computador

```bash
npx serve truco-do-pato
```

e abra o endereço que aparecer. Use o modo celular do DevTools (F12 → ícone de celular) pra ver como fica no telefone.

## Arquivos

| Arquivo | Pra quê |
| --- | --- |
| `index.html` | O app inteiro: layout, estilo, pato, som e lógica do placar |
| `manifest.webmanifest` | Nome, cores e ícones pra instalar na tela inicial |
| `sw.js` | Guarda o app no aparelho pra funcionar offline |
| `icons/` | Ícone do pato em SVG e PNG (192, 512 e 180 pro iPhone) |
