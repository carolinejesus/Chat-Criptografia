package com.mycompany.chat.pubsub.security;

import java.io.Serializable;

/**
 *
 * @author Carol
 */
public class PedidoCertificadoCliente implements Serializable {
    private final String nomeUsuario;
    private final String chavePublicaUsuarioBase64;
    
    public PedidoCertificadoCliente (String nomeUsuario, String chavePublicaUsuarioBase64){
        this.nomeUsuario = nomeUsuario;
        this.chavePublicaUsuarioBase64 = chavePublicaUsuarioBase64;
    }
    
    public String getNomeUsuario(){
        return nomeUsuario;
    }
    
    public String getChavePublicaUsuarioBase64(){
        return chavePublicaUsuarioBase64;
    }
    public String dadosParaAssinar(){
        return nomeUsuario + ":" + chavePublicaUsuarioBase64;
    }
}
