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
- Mantém a tela acesa durante o jogo.
- Cola das regras do truco paulista no menu.

O som do pato é gerado na hora pelo próprio navegador (Web Audio), sem arquivo de áudio.

## App Android (APK)

O APK fica em [`apk/TrucoDoPato.apk`](apk/TrucoDoPato.apk). Funciona do Android 7.0 em diante e não precisa de internet. O placar é desenhado pelo *Android System WebView* do celular, que precisa estar atualizado pela Play Store (versão 87 ou mais nova, o normal em qualquer celular com a Play Store). Se estiver muito antigo, o app avisa ao abrir.

Pra instalar:

1. Abra o link do arquivo no celular e toque em *View raw* ou no ícone de download (ou passe o arquivo pro celular de outro jeito).
2. Toque no arquivo baixado. Na primeira vez o Android pede pra **permitir instalar apps desta fonte** (do navegador ou do gerenciador de arquivos): permita e volte. No Android 7, o aviso leva às Configurações: ative *Fontes desconhecidas* e volte.
3. Toque em **Instalar**. Se o Play Protect avisar que não conhece o app, toque em *Mais detalhes → Instalar mesmo assim*. O aviso aparece pra qualquer app instalado fora da Play Store.

> **Samsung Galaxy:** se aparecer que a instalação foi bloqueada pelo *Bloqueador automático*, vá em *Configurações → Segurança e privacidade → Bloqueador automático*, desligue, instale o APK e ligue de novo depois, se quiser.

No APK, o "TRUCO!" é gritado pela voz do próprio Android (precisa ter a voz em português instalada; nas Configurações do celular, procure por *texto em voz*), a vibração é a nativa do celular, a tela fica sempre acesa e o botão voltar fecha o menu antes de sair do app (na tela de vitória, ele começa a próxima partida; o desfazer volta). O som do pato sai no volume de mídia.

O app só pede permissão pra vibrar. Não tem anúncio, não coleta nada e não acessa a internet.

### Gerar o APK de novo

O código do app Android está em [`android/`](android/). O APK é montado sem Android Studio, só com JDK 17+, `curl`, `python3` e `unzip` (Linux x86_64, WSL ou macOS):

```bash
cd truco-do-pato/android
KEYSTORE=/caminho/release.p12 ./build.sh
```

O script baixa as ferramentas do Maven Central (conferindo o SHA-256 de cada uma), empacota o `index.html` com as fontes embutidas, compila e assina o APK em `apk/TrucoDoPato.apk`.

**A chave de assinatura não fica no repositório.** O Android só aceita atualizar o app por cima se o APK novo for assinado com a mesma chave do instalado. O `apk/TrucoDoPato.apk` daqui foi assinado com o certificado de SHA-256 `76:7B:32:D0:F9:2C:CA:14:08:E5:1E:63:B5:B3:E9:D3:D0:84:E5:7F:C5:A0:76:FA:36:B3:CE:A3:DB:FF:81:BB` (apelido `truco`). Quem tem o arquivo `release.p12` dessa chave gera atualizações com o comando acima; guarde-o num lugar privado. Sem ele, `NEW_KEY=1 ./build.sh` cria uma chave nova, mas aí o app antigo precisa ser desinstalado antes (e o placar salvo se perde). Senha e apelido diferentes do padrão: `KEYSTORE_PASS=senha KEY_ALIAS=apelido`.

Os ícones são PNGs gerados por `android/tools/make_icons.cjs` (precisa do Playwright). Só rode de novo se mudar o desenho.

## Usar pelo navegador

O app web é um arquivo `index.html` só, sem dependências. Pra jogar pelo navegador:

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
| `apk/TrucoDoPato.apk` | O app Android pronto pra instalar |
| `android/` | Código do app Android: tela com o `index.html`, voz, vibração, ícones e o script de build |
| `android/fonts/` | Fontes Bungee e Rubik embutidas no APK, com as licenças (SIL OFL 1.1) |
