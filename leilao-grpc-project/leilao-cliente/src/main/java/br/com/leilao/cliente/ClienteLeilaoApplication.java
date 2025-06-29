package br.com.leilao.cliente;

import br.com.leilao.grpc.AtualizacaoLeilao;
import br.com.leilao.grpc.LanceRequest;
import br.com.leilao.grpc.LanceResponse;
import br.com.leilao.grpc.LeilaoServiceGrpc;
import br.com.leilao.grpc.AcompanharRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClienteLeilaoApplication {

    public static void main(String[] args) throws InterruptedException {
        // Cria o canal de comunicação com o servidor
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext() // Apenas para desenvolvimento
                .build();

        // Cria o stub para chamadas assíncronas/streaming
        LeilaoServiceGrpc.LeilaoServiceStub asyncStub = LeilaoServiceGrpc.newStub(channel);

        // Cria o stub para chamadas síncronas/blocking
        LeilaoServiceGrpc.LeilaoServiceBlockingStub blockingStub = LeilaoServiceGrpc.newBlockingStub(channel);

        System.out.print("Digite seu nome de usuário: ");
        Scanner scanner = new Scanner(System.in);
        String nomeUsuario = scanner.nextLine();

        System.out.println("--- Bem-vindo ao leilão, " + nomeUsuario + "! ---");

        // 1. Conecta-se para receber atualizações em uma thread separada
        acompanharLeilao(asyncStub);

        // 2. Loop principal para enviar lances
        System.out.println("Digite um valor para dar um lance ou 'sair' para fechar.");
        while (true) {
            String input = scanner.nextLine();
            if ("sair".equalsIgnoreCase(input)) {
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

        // Implementa o StreamObserver para lidar com as mensagens do servidor
        asyncStub.acompanharLeilao(request, new StreamObserver<>() {
            @Override
            public void onNext(AtualizacaoLeilao value) {
                // Mensagem recebida do servidor
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
