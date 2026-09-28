package servidor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class ServidorStatusUdp implements Runnable {

    private static final int TAMANHO_BUFFER = 1024;

    private final int portaUdp;
    private final ServidorReservas servidorReservas;

    public ServidorStatusUdp(
            int portaUdp,
            ServidorReservas servidorReservas) {

        this.portaUdp = portaUdp;
        this.servidorReservas = servidorReservas;
    }

    @Override
    public void run() {

        try (DatagramSocket socket =
                     new DatagramSocket(portaUdp)) {

            System.out.println(
                    "Status UDP ativo na porta " + portaUdp
            );

            byte[] buffer =
                    new byte[TAMANHO_BUFFER];

            while (true) {

                DatagramPacket pacoteRecebido =
                        new DatagramPacket(
                                buffer,
                                buffer.length
                        );

                socket.receive(pacoteRecebido);

                String comando =
                        new String(
                                pacoteRecebido.getData(),
                                pacoteRecebido.getOffset(),
                                pacoteRecebido.getLength(),
                                StandardCharsets.UTF_8
                        ).trim();

                servidorReservas.registrarRequisicaoUdp();

                String resposta;

                if (comando.equalsIgnoreCase("STATUS")) {

                    resposta =
                            servidorReservas.montarStatus()
                                    + "|PROTOCOLO=UDP";

                } else {

                    resposta =
                            "ERRO|comando UDP desconhecido";
                }

                byte[] dadosResposta =
                        resposta.getBytes(
                                StandardCharsets.UTF_8
                        );

                DatagramPacket pacoteResposta =
                        new DatagramPacket(
                                dadosResposta,
                                dadosResposta.length,
                                pacoteRecebido.getAddress(),
                                pacoteRecebido.getPort()
                        );

                socket.send(pacoteResposta);

                System.out.printf(
                        "[%s] UDP recebido de %s:%d%n",
                        Thread.currentThread().getName(),
                        pacoteRecebido
                                .getAddress()
                                .getHostAddress(),
                        pacoteRecebido.getPort()
                );

                /*
                 * Limpa o buffer antes da próxima mensagem.
                 */
                buffer = new byte[TAMANHO_BUFFER];
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro no servidor UDP: "
                            + e.getMessage()
            );
        }
    }
}