package br.com.leilao.cliente;

import br.com.leilao.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClienteLeilaoApplication {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 6565)
                .usePlaintext()
                .build();

        LeilaoServiceGrpc.LeilaoServiceStub asyncStub = LeilaoServiceGrpc.newStub(channel);
        LeilaoServiceGrpc.LeilaoServiceBlockingStub blockingStub = LeilaoServiceGrpc.newBlockingStub(channel);

        System.out.print("Digite seu nome de usuário: ");
        Scanner scanner = new Scanner(System.in);
        String nomeUsuario = scanner.nextLine();

        System.out.println("--- Bem-vindo ao leilão, " + nomeUsuario + "! ---");

        acompanharLeilao(asyncStub);

        System.out.println("Digite um valor para dar um lance, 'encerrar' para finalizar o leilão, ou 'sair' para sair.");
        while (true) {
            String input = scanner.nextLine();
            if ("sair".equalsIgnoreCase(input)) {
                break;
            } else if ("encerrar".equalsIgnoreCase(input)) {
                try {
                    ResultadoLeilao resultado = blockingStub.encerrarLeilao(EncerrarRequest.newBuilder().build());
                    System.out.println(">> Leilão encerrado!");
                    System.out.println("Ganhador: " + resultado.getGanhador());
                    System.out.printf("Valor do lance vencedor: R$ %.2f\n", resultado.getValorGanhador());
                    System.out.println("Lances realizados:");
                    for (Lance lance : resultado.getLancesList()) {
                        System.out.printf(" - %s: R$ %.2f\n", lance.getNomeUsuario(), lance.getValor());
                    }
                } catch (Exception e) {
                    System.out.println("Erro ao encerrar o leilão: " + e.getMessage());
                }
                break;
            }

            try {
                double valor = Double.parseDouble(input);
                LanceRequest request = LanceRequest.newBuilder()
                        .setValor(valor)
                        .setNomeUsuario(nomeUsuario)
                        .build();

                LanceResponse response = blockingStub.fazerLance(request);
                System.out.println(">> Resposta do Servidor: " + response.getMensagem());

            } catch (NumberFormatException e) {
                System.out.println("!! Erro: Por favor, digite um número válido.");
            } catch (Exception e) {
                log.error("Erro ao comunicar com o servidor: {}", e.getMessage());
                break;
            }
        }

        System.out.println("Encerrando cliente...");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void acompanharLeilao(LeilaoServiceGrpc.LeilaoServiceStub asyncStub) {
        AcompanharRequest request = AcompanharRequest.newBuilder().build();

        asyncStub.acompanharLeilao(request, new StreamObserver<>() {
            @Override
            public void onNext(AtualizacaoLeilao value) {
                System.out.printf("\n[ATUALIZAÇÃO] %s | Lance atual: R$ %.2f (por: %s)\n> ",
                        value.getMensagem(), value.getValorMinimoAtual(), value.getUltimoLicitante());
            }

            @Override
            public void onError(Throwable t) {
                log.error("Erro no stream do leilão: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("Stream do leilão foi encerrado pelo servidor.");
            }
        });
    }
}
