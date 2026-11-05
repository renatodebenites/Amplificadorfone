# Amplificador de Fone (Android)

Aumenta o volume do que toca no celular quando você usa fone. Uma tela: ganho de 0 a +15 dB e botão Ligar/Desligar.

## Como gerar o APK pelo GitHub (sem PC)
1. Crie um repositório novo no GitHub e envie o conteúdo desta pasta (pode ser pelo site: Add file > Upload files, incluindo a pasta `.github`).
2. Abra a aba Actions. O workflow "Gerar APK" roda sozinho e leva de 3 a 6 minutos.
3. Quando ficar verde, abra a execução e baixe `amplificador-fone-apk` em Artifacts.
4. Descompacte, passe o `app-debug.apk` para o celular e instale (permita fontes desconhecidas).

## Como gerar o APK no Android Studio
1. Instale o Android Studio no PC e abra esta pasta (File > Open).
2. Espere o Gradle sincronizar (precisa de internet na primeira vez).
3. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
4. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.
5. Passe para o celular e instale (permita "fontes desconhecidas" quando pedir).

## Como usar
1. Abra o app, escolha o ganho e toque em Ligar.
2. Pause e toque de novo a música ou o vídeo. O efeito pega quando o player começa a tocar.
3. Comece com ganho baixo. Ganho alto distorce e pode danificar fone e audição.

## Limites
- O Android moderno restringe efeitos globais de áudio. O app tenta dois caminhos: a mixagem geral
  (alguns aparelhos ignoram) e os players que anunciam a sessão de áudio (a maioria dos players de música).
- Apps que não anunciam a sessão e aparelhos que ignoram o efeito geral não serão amplificados.
- Requer Android 8 (API 26) ou mais novo.
