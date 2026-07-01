package com.mycompany.chat.pubsub.security;

import java.io.Serializable;

/**
 *
 * @author caroline.jesus
 */
public class CertificadoCliente implements Serializable {
    private final String nomeUsuario;
    private final String chavePublicaUsuarioBase64;
    private final String assinaturaBrokerBase64;
    
    public CertificadoCliente (String nomeUsuario, String chavePublicaUsuarioBase64, String assinaturaBrokerBase64){
        this.nomeUsuario = nomeUsuario;
        this.chavePublicaUsuarioBase64 = chavePublicaUsuarioBase64;
        this.assinaturaBrokerBase64 = assinaturaBrokerBase64;
    }
    
    public String getNomeUsuario() {
        return nomeUsuario;
    }

    public String getChavePublicaUsuarioBase64() {
        return chavePublicaUsuarioBase64;
    }

    public String getAssinaturaBrokerBase64() {
        return assinaturaBrokerBase64;
    }
    
    /**
     * Monta os dados para que o broker assine:
     * Esse mesmo formato deve ser usado tanto para assinar quanto para verificar assinatura
     * @return 
     */
    public String dadosParaAssinar() {
        return nomeUsuario + ":" + chavePublicaUsuarioBase64;
    }
}
