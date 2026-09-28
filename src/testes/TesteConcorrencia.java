package testes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteConcorrencia {

    private static final String HOST = "127.0.0.1";
    private static final int QUANTIDADE_CLIENTES = 3;
    private static final int RESERVAS_POR_CLIENTE = 10;

    public static void main(String[] args)
            throws InterruptedException {

        if (args.length != 1) {

            System.out.println(
                    "Uso: java testes.TesteConcorrencia <porta>"
            );

            System.out.println(
                    "Exemplo: java testes.TesteConcorrencia 5000"
            );

            return;
        }

        int porta = Integer.parseInt(args[0]);

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        QUANTIDADE_CLIENTES
                );

        CountDownLatch clientesProntos =
                new CountDownLatch(
                        QUANTIDADE_CLIENTES
                );

        CountDownLatch iniciarJuntos =
                new CountDownLatch(1);

        AtomicInteger respostasOk =
                new AtomicInteger();

        AtomicInteger respostasErro =
                new AtomicInteger();

        System.out.println("========================================");
        System.out.println(" TESTE DE CONCORRENCIA");
        System.out.println("========================================");
        System.out.println("Clientes: 3");
        System.out.println("Requisicoes por cliente: 10");
        System.out.println("Total de requisicoes: 30");
        System.out.println();

        for (int cliente = 1;
             cliente <= QUANTIDADE_CLIENTES;
             cliente++) {

            int numeroCliente = cliente;

            executor.submit(() ->
                    executarCliente(
                            porta,
                            numeroCliente,
                            clientesProntos,
                            iniciarJuntos,
                            respostasOk,
                            respostasErro
                    )
            );
        }

        /*
         * Aguarda os três clientes estabelecerem conexão.
         */
        clientesProntos.await();

        System.out.println(
                "Os tres clientes estao conectados."
        );

        System.out.println(
                "Liberando as 30 requisicoes..."
        );

        long inicio = System.currentTimeMillis();

        /*
         * Libera os três clientes ao mesmo tempo.
         */
        iniciarJuntos.countDown();

        executor.shutdown();

        executor.awaitTermination(
                1,
                TimeUnit.MINUTES
        );

        long tempo = System.currentTimeMillis() - inicio;

        System.out.println();
        System.out.println("========================================");
        System.out.println(" RESULTADO DO TESTE");
        System.out.println("========================================");
        System.out.println(
                "Requisicoes enviadas: 30"
        );
        System.out.println(
                "Respostas OK: " + respostasOk.get()
        );
        System.out.println(
                "Respostas com erro: " + respostasErro.get()
        );
        System.out.println(
                "Tempo total: " + tempo + " ms"
        );

        consultarResultadoFinal(porta);
    }

    private static void executarCliente(
            int porta,
            int numeroCliente,
            CountDownLatch clientesProntos,
            CountDownLatch iniciarJuntos,
            AtomicInteger respostasOk,
            AtomicInteger respostasErro) {

        String usuario =
                "cliente" + numeroCliente;

        try (
                Socket socket =
                        new Socket(HOST, porta);

                BufferedReader entrada =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                PrintWriter saida =
                        new PrintWriter(
                                socket.getOutputStream(),
                                true,
                                StandardCharsets.UTF_8
                        )
        ) {

            /*
             * Lê a mensagem inicial enviada pelo servidor.
             */
            entrada.readLine();

            clientesProntos.countDown();

            /*
             * Cada cliente aguarda a liberação do teste.
             */
            iniciarJuntos.await();

            int primeiroAssento =
                    (numeroCliente - 1)
                            * RESERVAS_POR_CLIENTE + 1;

            int ultimoAssento =
                    primeiroAssento
                            + RESERVAS_POR_CLIENTE - 1;

            for (int assento = primeiroAssento;
                 assento <= ultimoAssento;
                 assento++) {

                String comando =
                        "RESERVAR|"
                                + assento
                                + "|"
                                + usuario;

                saida.println(comando);

                String resposta =
                        entrada.readLine();

                if (resposta != null
                        && resposta.startsWith("OK")) {

                    respostasOk.incrementAndGet();

                } else {
                    respostasErro.incrementAndGet();
                }

                System.out.printf(
                        "[%s] assento=%d resposta=%s%n",
                        usuario,
                        assento,
                        resposta
                );
            }

            saida.println("SAIR");
            entrada.readLine();

        } catch (IOException e) {

            System.out.printf(
                    "[%s] Falha: %s%n",
                    usuario,
                    e.getMessage()
            );

            respostasErro.addAndGet(
                    RESERVAS_POR_CLIENTE
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.out.printf(
                    "[%s] Teste interrompido.%n",
                    usuario
            );
        }
    }

    private static void consultarResultadoFinal(
            int porta) {

        try (
                Socket socket =
                        new Socket(HOST, porta);

                BufferedReader entrada =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                PrintWriter saida =
                        new PrintWriter(
                                socket.getOutputStream(),
                                true,
                                StandardCharsets.UTF_8
                        )
        ) {

            entrada.readLine();

            saida.println("STATUS");

            String status =
                    entrada.readLine();

            saida.println("RESERVADOS");

            String reservados =
                    entrada.readLine();

            saida.println("SAIR");
            entrada.readLine();

            System.out.println(
                    "Status do servidor: " + status
            );

            System.out.println(
                    "Assentos registrados: " + reservados
            );

        } catch (IOException e) {

            System.out.println(
                    "Nao foi possivel consultar "
                            + "o resultado final: "
                            + e.getMessage()
            );
        }
    }
}