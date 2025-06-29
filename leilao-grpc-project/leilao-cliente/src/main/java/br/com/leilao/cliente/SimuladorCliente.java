package br.com.leilao.cliente;

import br.com.leilao.grpc.AcompanharRequest;
import br.com.leilao.grpc.AtualizacaoLeilao;
import br.com.leilao.grpc.LanceRequest;
import br.com.leilao.grpc.LeilaoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SimuladorCliente {

    // Usamos AtomicReference para guardar o último valor do leilão de forma segura entre as threads.
    // Uma thread estará ouvindo as atualizações do servidor e a outra estará fazendo os lances.
    private static final AtomicReference<BigDecimal> maiorLanceAtual = new AtomicReference<>(BigDecimal.ZERO);

    public static void main(String[] args) throws InterruptedException {
        Random random = new Random();
        String nomeBot = "Bot-" + random.nextInt(1000);
        log.info("Iniciando o simulador: {}", nomeBot);

        BigDecimal orcamentoMaximo = new BigDecimal("150.00").add(BigDecimal.valueOf(random.nextInt(150)));
        log.info("[{}] Meu orçamento máximo é de R$ {}", nomeBot, orcamentoMaximo.toPlainString());

        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 6565)
                .usePlaintext()
                .build();

        LeilaoServiceGrpc.LeilaoServiceStub asyncStub = LeilaoServiceGrpc.newStub(channel);
        LeilaoServiceGrpc.LeilaoServiceBlockingStub blockingStub = LeilaoServiceGrpc.newBlockingStub(channel);

        acompanharLeilao(asyncStub, nomeBot);

        while (true) {
            try {
                Thread.sleep(3000 + random.nextInt(5000));

                BigDecimal lanceBase = maiorLanceAtual.get();

                // Se o lance atual já for maior que o orçamento, o bot desiste.
                if (lanceBase.compareTo(orcamentoMaximo) > 0) {
                    log.info("[{}] O preço (R$ {}) ultrapassou meu orçamento (R$ {}). Desistindo do leilão.",
                            nomeBot, lanceBase.toPlainString(), orcamentoMaximo.toPlainString());
                    break; // Sai do loop de lances e encerra a atividade do bot.
                }

                BigDecimal novoLance = lanceBase.add(BigDecimal.valueOf(1 + random.nextInt(15)));

                // Garante que o bot não dê um lance acima do seu próprio orçamento
                if (novoLance.compareTo(orcamentoMaximo) > 0) {
                    novoLance = orcamentoMaximo;
                    log.info("[{}] Dando meu lance final e máximo de R$ {}", nomeBot, novoLance.toPlainString());
                }

                log.info("[{}] Tentando lance de R$ {}", nomeBot, novoLance.toPlainString());

                LanceRequest request = LanceRequest.newBuilder()
                        .setValor(novoLance.doubleValue())
                        .setNomeUsuario(nomeBot)
                        .build();

                blockingStub.fazerLance(request);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread do simulador foi interrompida.");
                break;
            } catch (Exception e) {
                log.error("[{}] Erro ao comunicar com o servidor: {}", nomeBot, e.getMessage());
                Thread.sleep(5000);
            }
        }

        channel.shutdown();
        log.info("[{}] Simulador encerrado.", nomeBot);
    }

    private static void acompanharLeilao(LeilaoServiceGrpc.LeilaoServiceStub asyncStub, String nomeBot) {
        AcompanharRequest request = AcompanharRequest.newBuilder().build();

        asyncStub.acompanharLeilao(request, new StreamObserver<>() {
            @Override
            public void onNext(AtualizacaoLeilao value) {
                // Quando uma nova atualização chega, guardamos o novo maior lance
                BigDecimal novoMaiorLance = BigDecimal.valueOf(value.getValorMinimoAtual());
                maiorLanceAtual.set(novoMaiorLance);
                log.info("[{}] Leilão atualizado! Maior lance agora é R$ {} por {}",
                        nomeBot, value.getValorMinimoAtual(), value.getUltimoLicitante());
            }

            @Override
            public void onError(Throwable t) {
                // Se der erro na conexão, apenas logamos. O loop principal tentará reconectar.
                maiorLanceAtual.set(BigDecimal.ZERO); // Reseta o valor para recomeçar
                log.error("[{}] Conexão com o stream do leilão perdida: {}", nomeBot, t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("[{}] O servidor encerrou o stream do leilão.", nomeBot);
            }
        });
    }
}