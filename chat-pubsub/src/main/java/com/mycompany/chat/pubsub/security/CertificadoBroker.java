package com.mycompany.chat.pubsub.security;

import java.io.Serializable;

/**
 * Representa o certificado logico do broker;
 * essa assinatura é usada para verificar se o broker é autorizado
 * pela CA.
 * @author Carol
 */
public class CertificadoBroker implements Serializable {
    private final String nomeBroker;
    private final String chavePublicaBrokerBase64;
    private final String assinaturaProfessorBase64;
    
    public CertificadoBroker(String nomeBroker, String chavePublicaBrokerBase64, String assinaturaProfessorBase64){
        this.nomeBroker = nomeBroker;
        this.chavePublicaBrokerBase64 = chavePublicaBrokerBase64;
        this.assinaturaProfessorBase64 = assinaturaProfessorBase64;
    }
    
    public String getNomeBroker(){
        return nomeBroker;
    }
    
    public String getChavePublicaBrokerBase64(){
        return chavePublicaBrokerBase64;
    }
    
    public String dadosParaAssinar(){
        return nomeBroker + ":" + chavePublicaBrokerBase64;
    }
    
    public String getAssinaturaProfessorBase64(){
        return assinaturaProfessorBase64;
    }
}
