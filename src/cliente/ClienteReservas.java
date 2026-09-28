package cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClienteReservas {

    private static final String HOST = "127.0.0.1";
    private static final int TEMPO_CONEXAO_MS = 2000;

    private final String usuario;
    private final int[] portas;

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;
    private int indicePortaAtual;

    public ClienteReservas(
            String usuario,
            int[] portas) {

        this.usuario = usuario;
        this.portas = portas;
        this.indicePortaAtual = -1;
    }

    private boolean conectar() {

        fecharConexao();

        for (int tentativa = 0;
             tentativa < portas.length;
             tentativa++) {

            int indice =
                    (indicePortaAtual + 1 + tentativa)
                            % portas.length;

            int porta = portas[indice];

            System.out.printf(
                    "Tentando conectar em %s:%d...%n",
                    HOST,
                    porta
            );

            try {

                Socket novoSocket = new Socket();

                novoSocket.connect(
                        new InetSocketAddress(HOST, porta),
                        TEMPO_CONEXAO_MS
                );

                BufferedReader novaEntrada =
                        new BufferedReader(
                                new InputStreamReader(
                                        novoSocket.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                PrintWriter novaSaida =
                        new PrintWriter(
                                novoSocket.getOutputStream(),
                                true,
                                StandardCharsets.UTF_8
                        );

                this.socket = novoSocket;
                this.entrada = novaEntrada;
                this.saida = novaSaida;
                this.indicePortaAtual = indice;

                String mensagemServidor =
                        entrada.readLine();

                System.out.println(
                        "Servidor: " + mensagemServidor
                );

                System.out.printf(
                        "Conectado com sucesso na porta %d.%n",
                        porta
                );

                return true;

            } catch (IOException e) {

                System.out.printf(
                        "Porta %d indisponivel.%n",
                        porta
                );
            }
        }

        System.out.println(
                "Nenhuma replica esta disponivel."
        );

        return false;
    }

    private String enviar(String comando)
            throws IOException {

        saida.println(comando);

        String resposta = entrada.readLine();

        if (resposta == null) {
            throw new IOException(
                    "servidor encerrou a conexao"
            );
        }

        return resposta;
    }

    private String enviarComRecuperacao(
            String comando) {

        try {
            return enviar(comando);

        } catch (IOException e) {

            System.out.println(
                    "A conexao com a replica foi perdida."
            );

            System.out.println(
                    "Tentando conectar a outra replica..."
            );

            if (!conectar()) {
                return "ERRO|nenhuma replica disponivel";
            }

            try {
                return enviar(comando);

            } catch (IOException segundoErro) {
                return "ERRO|falha ao reenviar o comando";
            }
        }
    }

    private void executar() {

        if (!conectar()) {
            return;
        }

        mostrarAjuda();

        try (Scanner teclado = new Scanner(System.in)) {

            while (true) {

                System.out.println();
                System.out.print("Digite um comando: ");

                String comandoDigitado =
                        teclado.nextLine().trim();

                if (comandoDigitado.isEmpty()) {
                    continue;
                }

                String comando =
                        prepararComando(comandoDigitado);

                if (comando == null) {
                    continue;
                }

                String resposta =
                        enviarComRecuperacao(comando);

                System.out.println(
                        "Resposta: " + resposta
                );

                if (comando.equalsIgnoreCase("SAIR")) {
                    break;
                }
            }
        }

        fecharConexao();

        System.out.println(
                "Cliente encerrado."
        );
    }

private String prepararComando(
        String comandoDigitado) {

    String texto = comandoDigitado.trim();

    /*
     * Permite digitar tanto:
     * reservar 5
     *
     * quanto:
     * RESERVAR|5
     */
    String[] partes;

    if (texto.contains("|")) {
        partes = texto.split("\\|");
    } else {
        partes = texto.split("\\s+");
    }

    String comando =
            partes[0].toUpperCase();

    if (comando.equals("RESERVAR")
            || comando.equals("CANCELAR")) {

        if (partes.length != 2) {

            System.out.println(
                    "Digite: "
                            + comando.toLowerCase()
                            + " numero"
            );

            return null;
        }

        return comando
                + "|"
                + partes[1]
                + "|"
                + usuario;
    }

    if (comando.equals("LISTAR")
            || comando.equals("RESERVADOS")
            || comando.equals("STATUS")
            || comando.equals("SAIR")) {

        return comando;
    }

    if (comando.equals("AJUDA")) {
        mostrarAjuda();
        return null;
    }

    System.out.println(
            "Comando desconhecido. Digite AJUDA."
    );

    return null;
}

private void mostrarAjuda() {

    System.out.println();
    System.out.println("Comandos disponiveis:");
    System.out.println(
            "listar       - mostra os assentos livres"
    );
    System.out.println(
            "reservar 5   - reserva o assento 5"
    );
    System.out.println(
            "cancelar 5   - cancela a reserva do assento 5"
    );
    System.out.println(
            "reservados   - mostra as reservas realizadas"
    );
    System.out.println(
            "status       - mostra o estado do servidor"
    );
    System.out.println(
            "ajuda        - mostra novamente os comandos"
    );
    System.out.println(
            "sair         - encerra o cliente"
    );
}

    private void fecharConexao() {

        try {
            if (entrada != null) {
                entrada.close();
            }
        } catch (IOException ignored) {
        }

        if (saida != null) {
            saida.close();
        }

        try {
            if (socket != null
                    && !socket.isClosed()) {

                socket.close();
            }
        } catch (IOException ignored) {
        }

        entrada = null;
        saida = null;
        socket = null;
    }

    public static void main(String[] args) {

        if (args.length < 2) {

            System.out.println(
                    "Uso: java cliente.ClienteReservas "
                            + "<usuario> <porta1> [porta2]"
            );

            System.out.println(
                    "Exemplo: java cliente.ClienteReservas "
                            + "guilherme 5000 5001"
            );

            return;
        }

        String usuario = args[0];

        int[] portas =
                new int[args.length - 1];

        for (int i = 1; i < args.length; i++) {
            portas[i - 1] =
                    Integer.parseInt(args[i]);
        }

        ClienteReservas cliente =
                new ClienteReservas(
                        usuario,
                        portas
                );

        cliente.executar();
    }
}