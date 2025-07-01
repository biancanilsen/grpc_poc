package br.com.leilao.cliente;

import br.com.leilao.grpc.AtualizacaoLeilao;
import br.com.leilao.grpc.LanceRequest;
import br.com.leilao.grpc.LeilaoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SimuladorCliente {

    private static final AtomicReference<BigDecimal> maiorLanceAtual = new AtomicReference<>(BigDecimal.ZERO);
    private static final AtomicBoolean leilaoAtivo = new AtomicBoolean(true);

    public static void main(String[] args) throws InterruptedException {
        Random random = new Random();
        String nomeBot = "Bot-" + random.nextInt(1000);
        log.info("Iniciando o simulador: {}", nomeBot);

        BigDecimal orcamentoMaximo = new BigDecimal("150.00")
                .add(BigDecimal.valueOf(random.nextInt(150)));
        log.info("[{}] Meu orçamento máximo é de R$ {}", nomeBot, orcamentoMaximo.toPlainString());

        var channel = ManagedChannelBuilder.forAddress("127.0.0.1", 6565)
                .usePlaintext()
                .build();

        var asyncStub = LeilaoServiceGrpc.newStub(channel);
        var blockingStub = LeilaoServiceGrpc.newBlockingStub(channel);

        acompanharLeilao(asyncStub, nomeBot);

        while (leilaoAtivo.get()) {
            try {
                Thread.sleep(3000 + random.nextInt(5000));

                BigDecimal atual = maiorLanceAtual.get();
                if (atual.compareTo(orcamentoMaximo) > 0) {
                    log.info("[{}] Lance atual R$ {} > orçamento R$ {} ⇒ desistindo.", 
                             nomeBot, atual, orcamentoMaximo);
                    break;
                }

                BigDecimal novo = atual.add(BigDecimal.valueOf(1 + random.nextInt(15)));
                if (novo.compareTo(orcamentoMaximo) > 0) {
                    novo = orcamentoMaximo;
                    log.info("[{}] Dando lance final: R$ {}", nomeBot, novo);
                }

                log.info("[{}] Tentando lance: R$ {}", nomeBot, novo);
                var req = LanceRequest.newBuilder()
                        .setValor(novo.doubleValue())
                        .setNomeUsuario(nomeBot)
                        .build();
                blockingStub.fazerLance(req);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrompida, encerrando bot.");
                break;
            } catch (Exception e) {
                log.error("[{}] Erro: {} — tentando reconectar após 5s", nomeBot, e.getMessage());
                Thread.sleep(5000);
            }
        }

        channel.shutdown();
        log.info("[{}] Simulador finalizado.", nomeBot);
    }

    private static void acompanharLeilao(LeilaoServiceGrpc.LeilaoServiceStub asyncStub, String nomeBot) {
        asyncStub.acompanharLeilao(
            br.com.leilao.grpc.AcompanharRequest.newBuilder().build(),
            new StreamObserver<>() {
                @Override
                public void onNext(AtualizacaoLeilao v) {
                    BigDecimal novo = BigDecimal.valueOf(v.getValorMinimoAtual());
                    maiorLanceAtual.set(novo);
                    log.info("[{}] Atualização: R$ {} por {}", nomeBot, novo, v.getUltimoLicitante());
                }

                @Override
                public void onError(Throwable t) {
                    leilaoAtivo.set(false);
                    log.error("[{}] Erro no stream: {}", nomeBot, t.getMessage());
                }

                @Override
                public void onCompleted() {
                    leilaoAtivo.set(false);
                    log.info("[{}] LEILÃO ENCERRADO PELO SERVIDOR!", nomeBot);
                }
            }
        );
    }
}
