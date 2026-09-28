package servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ServidorReservas {

    private static final int QUANTIDADE_ASSENTOS = 50;
    private static final String HOST_PAR = "127.0.0.1";
    private static final int TIMEOUT_CONEXAO_MS = 1000;
    private static final int TIMEOUT_RESPOSTA_MS = 2000;

    private final String nomeReplica;
    private final int portaTcp;
    private final int portaUdp;
    private final String papelInicial;
    private final int portaTcpPar;

    private final GerenciadorReservas gerenciador;

    private final AtomicInteger clientesConectados;
    private final AtomicLong requisicoesRecebidas;
    private final AtomicLong sequenciaClientes;

    private volatile boolean primariaDisponivel;

    public ServidorReservas(
            String nomeReplica,
            int portaTcp,
            int portaUdp,
            boolean sincronizacaoAtiva,
            String papelInicial,
            int portaTcpPar) {

        this.nomeReplica = nomeReplica;
        this.portaTcp = portaTcp;
        this.portaUdp = portaUdp;
        this.papelInicial =
                papelInicial.toUpperCase();
        this.portaTcpPar = portaTcpPar;

        this.gerenciador =
                new GerenciadorReservas(
                        QUANTIDADE_ASSENTOS,
                        sincronizacaoAtiva
                );

        this.clientesConectados =
                new AtomicInteger(0);

        this.requisicoesRecebidas =
                new AtomicLong(0);

        this.sequenciaClientes =
                new AtomicLong(0);

        this.primariaDisponivel =
                papelInicial.equalsIgnoreCase("backup");
    }

    public void iniciar() {

        System.out.println(
                "========================================"
        );

        System.out.println(
                " SISTEMA DISTRIBUIDO DE RESERVAS"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Replica: " + nomeReplica
        );

        System.out.println(
                "Papel: " + papelInicial
        );

        System.out.println(
                "Porta TCP: " + portaTcp
        );

        System.out.println(
                "Porta UDP: " + portaUdp
        );

        System.out.println(
                "Porta da outra replica: "
                        + portaTcpPar
        );

        System.out.println(
                "Sincronizacao: "
                        + (gerenciador
                        .isSincronizacaoAtiva()
                        ? "ATIVA"
                        : "DESATIVADA")
        );

        System.out.println(
                "Quantidade de assentos: "
                        + gerenciador
                        .getQuantidadeAssentos()
        );

        System.out.println(
                "Aguardando clientes..."
        );

        System.out.println();

        Thread threadUdp =
                new Thread(
                        new ServidorStatusUdp(
                                portaUdp,
                                this
                        ),
                        nomeReplica + "-status-udp"
                );

        threadUdp.setDaemon(true);
        threadUdp.start();

        try (ServerSocket servidor =
                     new ServerSocket(portaTcp)) {

            while (true) {

                Socket cliente =
                        servidor.accept();

                clientesConectados
                        .incrementAndGet();

                long numeroCliente =
                        sequenciaClientes
                                .incrementAndGet();

                String nomeThread =
                        nomeReplica
                                + "-cliente-"
                                + numeroCliente;

                Thread thread =
                        new Thread(
                                new AtendenteCliente(
                                        cliente
                                ),
                                nomeThread
                        );

                thread.start();
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro no servidor "
                            + nomeReplica
                            + ": "
                            + e.getMessage()
            );
        }
    }

    private class AtendenteCliente
            implements Runnable {

        private final Socket socket;

        private AtendenteCliente(
                Socket socket) {

            this.socket = socket;
        }

        @Override
        public void run() {

            String enderecoCliente =
                    socket
                            .getRemoteSocketAddress()
                            .toString();

            System.out.printf(
                    "[%s] Cliente conectado: %s%n",
                    Thread.currentThread()
                            .getName(),
                    enderecoCliente
            );

            try (
                    socket;

                    BufferedReader entrada =
                            new BufferedReader(
                                    new InputStreamReader(
                                            socket
                                            .getInputStream(),
                                            StandardCharsets
                                                    .UTF_8
                                    )
                            );

                    PrintWriter saida =
                            new PrintWriter(
                                    socket
                                    .getOutputStream(),
                                    true,
                                    StandardCharsets
                                            .UTF_8
                            )
            ) {

                saida.println(
                        "OK|conectado a replica "
                                + nomeReplica
                                + "|PAPEL="
                                + papelAtual()
                );

                String mensagem;

                while ((mensagem =
                                entrada.readLine())
                        != null) {

                    mensagem =
                            mensagem.trim();

                    if (mensagem.isEmpty()) {
                        continue;
                    }

                    requisicoesRecebidas
                            .incrementAndGet();

                    System.out.printf(
                            "[%s] Recebido: %s%n",
                            Thread.currentThread()
                                    .getName(),
                            mensagem
                    );

                    String resposta =
                            processarComando(
                                    mensagem
                            );

                    saida.println(resposta);

                    if (mensagem
                            .equalsIgnoreCase(
                                    "SAIR"
                            )) {

                        break;
                    }
                }

            } catch (IOException e) {

                System.out.printf(
                        "[%s] Cliente desconectado: %s%n",
                        Thread.currentThread()
                                .getName(),
                        e.getMessage()
                );

            } finally {

                int ativos =
                        clientesConectados
                                .decrementAndGet();

                System.out.printf(
                        "[%s] Conexao encerrada. "
                                + "Clientes ativos: %d%n",
                        Thread.currentThread()
                                .getName(),
                        ativos
                );
            }
        }
    }

    private String processarComando(
            String mensagem) {

        String[] partes =
                mensagem.split("\\|");

        String comando =
                partes[0].toUpperCase();

        try {

            return switch (comando) {

                case "LISTAR" ->
                        gerenciador
                                .listarDisponiveis();

                case "RESERVADOS" ->
                        gerenciador
                                .listarReservados();

                case "RESERVAR" -> {

                    if (partes.length != 3) {

                        yield "ERRO|use "
                                + "RESERVAR|assento|usuario";
                    }

                    int assento =
                            Integer.parseInt(
                                    partes[1]
                            );

                    String usuario =
                            partes[2];

                    yield processarReserva(
                            assento,
                            usuario
                    );
                }

                case "CANCELAR" -> {

                    if (partes.length != 3) {

                        yield "ERRO|use "
                                + "CANCELAR|assento|usuario";
                    }

                    int assento =
                            Integer.parseInt(
                                    partes[1]
                            );

                    String usuario =
                            partes[2];

                    yield processarCancelamento(
                            assento,
                            usuario
                    );
                }

                case "REPLICAR_RESERVA" -> {

                    if (partes.length != 3) {

                        yield "ERRO|replicacao invalida";
                    }

                    int assento =
                            Integer.parseInt(
                                    partes[1]
                            );

                    String usuario =
                            partes[2];

                    String resultado =
                            gerenciador.reservar(
                                    assento,
                                    usuario
                            );

                    System.out.printf(
                            "[%s] Estado replicado: "
                                    + "reserva do assento %d%n",
                            nomeReplica,
                            assento
                    );

                    yield resultado;
                }

                case "REPLICAR_CANCELAMENTO" -> {

                    if (partes.length != 3) {

                        yield "ERRO|replicacao invalida";
                    }

                    int assento =
                            Integer.parseInt(
                                    partes[1]
                            );

                    String usuario =
                            partes[2];

                    String resultado =
                            gerenciador.cancelar(
                                    assento,
                                    usuario
                            );

                    System.out.printf(
                            "[%s] Estado replicado: "
                                    + "cancelamento "
                                    + "do assento %d%n",
                            nomeReplica,
                            assento
                    );

                    yield resultado;
                }

                case "STATUS" ->
                        montarStatus();

                case "SAIR" ->
                        "BYE";

                default ->
                        "ERRO|comando desconhecido";
            };

        } catch (NumberFormatException e) {

            return "ERRO|numero "
                    + "do assento invalido";
        }
    }

    private String processarReserva(
            int assento,
            String usuario) {

        if (ehBackup()
                && primariaDisponivel) {

            String respostaPrimaria =
                    enviarParaOutraReplica(
                            "RESERVAR|"
                                    + assento
                                    + "|"
                                    + usuario
                    );

            if (respostaPrimaria != null) {

                System.out.println(
                        "[" + nomeReplica + "] "
                                + "Requisicao encaminhada "
                                + "para a primaria."
                );

                return respostaPrimaria;
            }

            promoverBackup();
        }

        String resposta =
                gerenciador.reservar(
                        assento,
                        usuario
                );

        if (resposta.startsWith("OK")
                && ehPrimaria()) {

            replicarReserva(
                    assento,
                    usuario
            );
        }

        return resposta;
    }

    private String processarCancelamento(
            int assento,
            String usuario) {

        if (ehBackup()
                && primariaDisponivel) {

            String respostaPrimaria =
                    enviarParaOutraReplica(
                            "CANCELAR|"
                                    + assento
                                    + "|"
                                    + usuario
                    );

            if (respostaPrimaria != null) {

                System.out.println(
                        "[" + nomeReplica + "] "
                                + "Cancelamento encaminhado "
                                + "para a primaria."
                );

                return respostaPrimaria;
            }

            promoverBackup();
        }

        String resposta =
                gerenciador.cancelar(
                        assento,
                        usuario
                );

        if (resposta.startsWith("OK")
                && ehPrimaria()) {

            replicarCancelamento(
                    assento,
                    usuario
            );
        }

        return resposta;
    }

    private void replicarReserva(
            int assento,
            String usuario) {

        String resposta =
                enviarParaOutraReplica(
                        "REPLICAR_RESERVA|"
                                + assento
                                + "|"
                                + usuario
                );

        if (resposta != null
                && resposta.startsWith("OK")) {

            System.out.printf(
                    "[%s] Reserva do assento %d "
                            + "confirmada no backup.%n",
                    nomeReplica,
                    assento
            );

        } else {

            System.out.printf(
                    "[%s] Backup indisponivel. "
                            + "Reserva mantida na primaria.%n",
                    nomeReplica
            );
        }
    }

    private void replicarCancelamento(
            int assento,
            String usuario) {

        String resposta =
                enviarParaOutraReplica(
                        "REPLICAR_CANCELAMENTO|"
                                + assento
                                + "|"
                                + usuario
                );

        if (resposta != null
                && resposta.startsWith("OK")) {

            System.out.printf(
                    "[%s] Cancelamento do assento %d "
                            + "confirmado no backup.%n",
                    nomeReplica,
                    assento
            );

        } else {

            System.out.printf(
                    "[%s] Backup indisponivel. "
                            + "Cancelamento mantido "
                            + "na primaria.%n",
                    nomeReplica
            );
        }
    }

    private String enviarParaOutraReplica(
            String comando) {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(
                            HOST_PAR,
                            portaTcpPar
                    ),
                    TIMEOUT_CONEXAO_MS
            );

            socket.setSoTimeout(
                    TIMEOUT_RESPOSTA_MS
            );

            try (
                    BufferedReader entrada =
                            new BufferedReader(
                                    new InputStreamReader(
                                            socket
                                            .getInputStream(),
                                            StandardCharsets
                                                    .UTF_8
                                    )
                            );

                    PrintWriter saida =
                            new PrintWriter(
                                    socket
                                    .getOutputStream(),
                                    true,
                                    StandardCharsets
                                            .UTF_8
                            )
            ) {

                /*
                 * Lê a mensagem inicial da outra réplica.
                 */
                entrada.readLine();

                saida.println(comando);

                return entrada.readLine();
            }

        } catch (IOException e) {

            return null;
        }
    }

    private void promoverBackup() {

        primariaDisponivel = false;

        System.out.println(
                "========================================"
        );

        System.out.println(
                " FALHA DA PRIMARIA DETECTADA"
        );

        System.out.println(
                " Replica " + nomeReplica
                        + " promovida para PRIMARIA"
        );

        System.out.println(
                "========================================"
        );
    }

    private boolean ehPrimaria() {

        return papelInicial
                .equalsIgnoreCase("primaria");
    }

    private boolean ehBackup() {

        return papelInicial
                .equalsIgnoreCase("backup");
    }

    private String papelAtual() {

        if (ehBackup()
                && !primariaDisponivel) {

            return "PRIMARIA_PROMOVIDA";
        }

        return papelInicial;
    }

    public void registrarRequisicaoUdp() {

        requisicoesRecebidas
                .incrementAndGet();
    }

    public String montarStatus() {

        return "STATUS"
                + "|REPLICA=" + nomeReplica
                + "|PAPEL=" + papelAtual()
                + "|CLIENTES="
                + clientesConectados.get()
                + "|REQ="
                + requisicoesRecebidas.get()
                + "|RESERVAS="
                + gerenciador
                .getTotalReservas()
                + "|SYNC="
                + (gerenciador
                .isSincronizacaoAtiva()
                ? "ATIVA"
                : "DESATIVADA")
                + "|ATIVO";
    }

    public static void main(String[] args) {

        if (args.length != 6) {

            System.out.println(
                    "Uso: java servidor.ServidorReservas "
                            + "<replica> <portaTCP> "
                            + "<portaUDP> <com|sem> "
                            + "<primaria|backup> "
                            + "<portaTCPPar>"
            );

            System.out.println(
                    "Primaria: A 5000 6000 "
                            + "com primaria 5001"
            );

            System.out.println(
                    "Backup: B 5001 6001 "
                            + "com backup 5000"
            );

            return;
        }

        String nomeReplica = args[0];

        int portaTcp =
                Integer.parseInt(args[1]);

        int portaUdp =
                Integer.parseInt(args[2]);

        boolean sincronizacaoAtiva =
                args[3]
                        .equalsIgnoreCase("com");

        String papelInicial =
                args[4];

        int portaTcpPar =
                Integer.parseInt(args[5]);

        ServidorReservas servidor =
                new ServidorReservas(
                        nomeReplica,
                        portaTcp,
                        portaUdp,
                        sincronizacaoAtiva,
                        papelInicial,
                        portaTcpPar
                );

        servidor.iniciar();
    }
}