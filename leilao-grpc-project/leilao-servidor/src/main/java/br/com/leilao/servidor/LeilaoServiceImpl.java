package br.com.leilao.servidor;

import br.com.leilao.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@GRpcService
@RequiredArgsConstructor
public class LeilaoServiceImpl extends LeilaoServiceGrpc.LeilaoServiceImplBase {

    private final LeilaoManager leilaoManager;

    @Override
    public void fazerLance(LanceRequest request, StreamObserver<LanceResponse> responseObserver) {
        log.info("Lance recebido de {}: R$ {}", request.getNomeUsuario(), request.getValor());

        boolean sucesso = leilaoManager.novoLance(BigDecimal.valueOf(request.getValor()), request.getNomeUsuario());

        LanceResponse response;
        if (sucesso) {
            response = LanceResponse.newBuilder()
                    .setSucesso(true)
                    .setMensagem("Lance aceito!")
                    .build();
        } else {
            response = LanceResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Lance recusado. O valor precisa ser maior que o atual.")
                    .build();
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void acompanharLeilao(AcompanharRequest request, StreamObserver<AtualizacaoLeilao> responseObserver) {
        log.info("Novo cliente acompanhando o leilão.");
        leilaoManager.adicionarObservador(responseObserver);
    }

   @Override
public void encerrarLeilao(EncerrarRequest request, StreamObserver<ResultadoLeilao> responseObserver) {
    log.info("Encerrando o leilão...");

    LanceInfo maiorLance = leilaoManager.getMaiorLance();
    List<LanceInfo> todosLances = leilaoManager.getTodosLances();

    ResultadoLeilao.Builder resultadoBuilder = ResultadoLeilao.newBuilder()
            .setGanhador(maiorLance.getNomeUsuario())
            .setValorGanhador(maiorLance.getValor().doubleValue());

    if (maiorLance == null) {
        responseObserver.onError(Status.FAILED_PRECONDITION
        .withDescription("Nenhum lance registrado.")
        .asRuntimeException());
        return;
    }

    for (LanceInfo lance : todosLances) {
        resultadoBuilder.addLances(Lance.newBuilder()
                .setNomeUsuario(lance.getNomeUsuario())
                .setValor(lance.getValor().doubleValue())
                .build());
    }

    responseObserver.onNext(resultadoBuilder.build());
    responseObserver.onCompleted();

    leilaoManager.notificarEncerramento(resultadoBuilder.build());
}

}
