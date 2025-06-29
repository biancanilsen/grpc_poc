package br.com.leilao.servidor;

import br.com.leilao.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;

import java.math.BigDecimal;

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

        // A conexão permanecerá aberta. O LeilaoManager cuidará de enviar os dados.
        // Precisamos detectar quando o cliente se desconecta, mas o gRPC faz isso
        // gerando um erro/cancelamento que pode ser tratado. No entanto, para
        // simplicidade, a remoção pode ser feita em um gerenciador de conexões mais robusto.
    }
}
