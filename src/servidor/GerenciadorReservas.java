package servidor;

import java.util.ArrayList;
import java.util.List;

public class GerenciadorReservas {

    private final String[] assentos;
    private final boolean sincronizacaoAtiva;

    private int totalReservas;

    public GerenciadorReservas(
            int quantidadeAssentos,
            boolean sincronizacaoAtiva) {

        this.assentos = new String[quantidadeAssentos];
        this.sincronizacaoAtiva = sincronizacaoAtiva;
        this.totalReservas = 0;
    }

    public String reservar(int numeroAssento, String usuario) {

        if (sincronizacaoAtiva) {
            synchronized (this) {
                return executarReserva(numeroAssento, usuario);
            }
        }

        return executarReserva(numeroAssento, usuario);
    }

    private String executarReserva(
            int numeroAssento,
            String usuario) {

        if (!assentoValido(numeroAssento)) {
            return "ERRO|assento inexistente";
        }

        int indice = numeroAssento - 1;

if (assentos[indice] != null) {

    if (assentos[indice].equals(usuario)) {

        return "OK|assento "
                + numeroAssento
                + " ja estava reservado para "
                + usuario;
    }

    return "ERRO|assento ja reservado por "
            + assentos[indice];
}

        int valorAntes = totalReservas;

        /*
         * pausa para aumentar a possibilidade de duas ou mais
         * threads acessarem a região crítica ao mesmo tempo.
         */
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assentos[indice] = usuario;
        totalReservas = valorAntes + 1;

        System.out.printf(
                "[%s] RESERVAR assento=%d usuario=%s "
                        + "contadorAntes=%d contadorDepois=%d%n",
                Thread.currentThread().getName(),
                numeroAssento,
                usuario,
                valorAntes,
                totalReservas
        );

        return "OK|assento " + numeroAssento
                + " reservado para " + usuario;
    }

    public String cancelar(int numeroAssento, String usuario) {

        if (sincronizacaoAtiva) {
            synchronized (this) {
                return executarCancelamento(numeroAssento, usuario);
            }
        }

        return executarCancelamento(numeroAssento, usuario);
    }

    private String executarCancelamento(
            int numeroAssento,
            String usuario) {

        if (!assentoValido(numeroAssento)) {
            return "ERRO|assento inexistente";
        }

        int indice = numeroAssento - 1;
        String proprietario = assentos[indice];

        if (proprietario == null) {
            return "ERRO|assento nao esta reservado";
        }

        if (!proprietario.equals(usuario)) {
            return "ERRO|reserva pertence a outro usuario";
        }

        int valorAntes = totalReservas;

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assentos[indice] = null;
        totalReservas = valorAntes - 1;

        System.out.printf(
                "[%s] CANCELAR assento=%d usuario=%s "
                        + "contadorAntes=%d contadorDepois=%d%n",
                Thread.currentThread().getName(),
                numeroAssento,
                usuario,
                valorAntes,
                totalReservas
        );

        return "OK|reserva do assento "
                + numeroAssento + " cancelada";
    }

    public synchronized String listarDisponiveis() {

        List<Integer> disponiveis = new ArrayList<>();

        for (int i = 0; i < assentos.length; i++) {
            if (assentos[i] == null) {
                disponiveis.add(i + 1);
            }
        }

        StringBuilder resposta = new StringBuilder("LISTA|");

        for (int i = 0; i < disponiveis.size(); i++) {
            if (i > 0) {
                resposta.append(",");
            }

            resposta.append(disponiveis.get(i));
        }

        return resposta.toString();
    }

    public synchronized String listarReservados() {

        StringBuilder resposta = new StringBuilder("RESERVADOS|");
        boolean primeiro = true;

        for (int i = 0; i < assentos.length; i++) {
            if (assentos[i] != null) {

                if (!primeiro) {
                    resposta.append(",");
                }

                resposta.append(i + 1)
                        .append(":")
                        .append(assentos[i]);

                primeiro = false;
            }
        }

        return resposta.toString();
    }

    public int getTotalReservas() {
        return totalReservas;
    }

    public int getQuantidadeAssentos() {
        return assentos.length;
    }

    public boolean isSincronizacaoAtiva() {
        return sincronizacaoAtiva;
    }

    private boolean assentoValido(int numeroAssento) {
        return numeroAssento >= 1
                && numeroAssento <= assentos.length;
    }
}