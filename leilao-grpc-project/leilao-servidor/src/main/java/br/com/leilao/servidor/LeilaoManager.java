package br.com.leilao.servidor;

import br.com.leilao.grpc.AtualizacaoLeilao;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LeilaoManager {

    // Guarda o valor do lance mais alto de forma atômica/thread-safe
    private final AtomicReference<BigDecimal> valorAtual = new AtomicReference<>(new BigDecimal("99.99"));

    @Getter
    private final AtomicReference<String> ultimoLicitante = new AtomicReference<>("Sistema");

    // Guarda todos os clientes que estão "ouvindo" as atualizações.
    // ConcurrentHashMap.newKeySet() cria um Set thread-safe.
    private final Set<StreamObserver<AtualizacaoLeilao>> observadores = ConcurrentHashMap.newKeySet();

    public synchronized boolean novoLance(BigDecimal valor, String nomeUsuario) {
        if (valor.compareTo(valorAtual.get()) > 0) {
            this.valorAtual.set(valor);
            this.ultimoLicitante.set(nomeUsuario);
            notificarObservadores(nomeUsuario);
            return true;
        }
        return false;
    }

    public void adicionarObservador(StreamObserver<AtualizacaoLeilao> observador) {
        observadores.add(observador);
        // Envia o estado atual assim que o cliente se conecta
        observador.onNext(getAtualizacaoAtual("Bem-vindo ao leilão!"));
    }

    public void removerObservador(StreamObserver<AtualizacaoLeilao> observador) {
        observadores.remove(observador);
    }

    private void notificarObservadores(String licitante) {
        AtualizacaoLeilao atualizacao = getAtualizacaoAtual("Novo lance de " + licitante + "!");
        observadores.forEach(obs -> obs.onNext(atualizacao));
    }

    private AtualizacaoLeilao getAtualizacaoAtual(String mensagem) {
        return AtualizacaoLeilao.newBuilder()
                .setValorMinimoAtual(valorAtual.get().doubleValue())
                .setUltimoLicitante(ultimoLicitante.get())
                .setMensagem(mensagem)
                .build();
    }
}