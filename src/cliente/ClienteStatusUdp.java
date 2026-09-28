package cliente;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class ClienteStatusUdp {

    private static final String HOST = "127.0.0.1";
    private static final int TAMANHO_BUFFER = 1024;
    private static final int TEMPO_LIMITE_MS = 2000;

    public static void main(String[] args)
            throws Exception {

        if (args.length != 1) {

            System.out.println(
                    "Uso: java cliente.ClienteStatusUdp "
                            + "<portaUDP>"
            );

            System.out.println(
                    "Exemplo: java cliente.ClienteStatusUdp 6000"
            );

            return;
        }

        int portaUdp =
                Integer.parseInt(args[0]);

        try (DatagramSocket socket =
                     new DatagramSocket()) {

            socket.setSoTimeout(TEMPO_LIMITE_MS);

            byte[] dados =
                    "STATUS".getBytes(
                            StandardCharsets.UTF_8
                    );

            DatagramPacket envio =
                    new DatagramPacket(
                            dados,
                            dados.length,
                            InetAddress.getByName(HOST),
                            portaUdp
                    );

            long inicio =
                    System.currentTimeMillis();

            socket.send(envio);

            byte[] buffer =
                    new byte[TAMANHO_BUFFER];

            DatagramPacket recebimento =
                    new DatagramPacket(
                            buffer,
                            buffer.length
                    );

            try {

                socket.receive(recebimento);

                long tempo =
                        System.currentTimeMillis()
                                - inicio;

                String resposta =
                        new String(
                                recebimento.getData(),
                                recebimento.getOffset(),
                                recebimento.getLength(),
                                StandardCharsets.UTF_8
                        );

                System.out.println(
                        "Resposta UDP: " + resposta
                );

                System.out.println(
                        "Tempo: " + tempo + " ms"
                );

            } catch (SocketTimeoutException e) {

                System.out.println(
                        "A replica nao respondeu "
                                + "dentro de "
                                + TEMPO_LIMITE_MS
                                + " ms."
                );
            }
        }
    }
}