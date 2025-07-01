package br.com.leilao.servidor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@ToString
@AllArgsConstructor
public class LanceInfo {
    private final String nomeUsuario;
    private final BigDecimal valor;
}
