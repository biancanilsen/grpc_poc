package br.com.leilao.servidor;

import br.com.leilao.grpc.ResultadoLeilao;
import br.com.leilao.grpc.AtualizacaoLeilao;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LeilaoManager {

    private final AtomicReference<BigDecimal> valorAtual = new AtomicReference<>(new BigDecimal("99.99"));
    private final Set<StreamObserver<AtualizacaoLeilao>> observadores = ConcurrentHashMap.newKeySet();
    private final List<LanceInfo> lances = new ArrayList<>();

    @Getter
    private final AtomicReference<String> ultimoLicitante = new AtomicReference<>("Sistema");

    public synchronized boolean novoLance(BigDecimal valor, String nomeUsuario) {
    LanceInfo atualMaior = getMaiorLance();
    if (atualMaior == null || valor.compareTo(atualMaior.getValor()) > 0) {
        lances.add(new LanceInfo(nomeUsuario, valor));
        notificarTodos(nomeUsuario, valor);
        return true;
    }
    return false;
}

    public void adicionarObservador(StreamObserver<AtualizacaoLeilao> observador) {
        observadores.add(observador);

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

    public synchronized LanceInfo getMaiorLance() {
        return lances.stream().max(Comparator.comparing(LanceInfo::getValor)).orElse(null);
    }

    public synchronized List<LanceInfo> getTodosLances() {
        return new ArrayList<>(lances);
    }

    private void notificarTodos(String nome, BigDecimal valor) {
        AtualizacaoLeilao atualizacao = AtualizacaoLeilao.newBuilder()
                .setValorMinimoAtual(valor.doubleValue())
                .setUltimoLicitante(nome)
                .setMensagem("Novo lance!")
                .build();
        observadores.forEach(obs -> {
            try {
                obs.onNext(atualizacao);
            } catch (Exception e) {
            }
        });
    }

    public synchronized void notificarEncerramento(ResultadoLeilao resultado) {
        observadores.forEach(obs -> {
            try {
                obs.onCompleted(); 
            } catch (Exception e) {
            }
        });
        observadores.clear();
    }
}