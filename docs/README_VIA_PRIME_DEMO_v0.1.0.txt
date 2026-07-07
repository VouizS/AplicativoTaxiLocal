VIA PRIME DEMO v0.1.0 - Real Map LAN Prototype

Objetivo:
Demo funcional real para apresentação de aplicativo de transporte executivo/táxi local.

Recursos reais nesta versão:
- Mapa real com OpenStreetMap/osmdroid.
- Localização real do aparelho com permissão Android.
- Centralização automática do mapa no ponto real do usuário.
- Modo Cliente, Motorista e Central/Admin no mesmo APK.
- Servidor LAN local dentro do próprio app para teste em celulares na mesma rede.
- Cliente solicita transporte com latitude/longitude real.
- Motorista fica online, aceita atendimento, envia localização real e marca chegada.
- Cliente vê marcador do motorista no mapa quando conectado ao mesmo servidor LAN.
- Aviso local quando o motorista marca chegada.
- Visual executivo preto/dourado inspirado no pedido da Via Prime, sem copiar arte.

Como testar em 2 aparelhos:
1. Instale o mesmo APK nos dois celulares.
2. No celular do motorista ou em um terceiro aparelho, abra Central/Admin.
3. Toque em "Iniciar servidor local da demo".
4. Anote o IP exibido, exemplo: 192.168.0.15:8080.
5. No aparelho Cliente, toque LAN e salve: http://192.168.0.15:8080.
6. No aparelho Motorista, toque LAN e salve o mesmo endereço.
7. Cliente solicita transporte.
8. Motorista fica online e aceita.
9. Ambos precisam estar na mesma rede Wi-Fi/hotspot.

Limitação honesta da v0.1:
Esta versão usa servidor local LAN para demonstrar sincronização real sem depender de nuvem.
Para produção externa, a próxima etapa deve migrar para backend em nuvem com autenticação, banco e painel.

Permissões:
- Internet: mapa e sincronização LAN.
- Localização: ponto real do cliente/motorista no mapa.
- Notificações: aviso local de chegada.

Não usa:
SMS, contatos, câmera, microfone, fotos, arquivos, acessibilidade, administrador do dispositivo ou overlay.
