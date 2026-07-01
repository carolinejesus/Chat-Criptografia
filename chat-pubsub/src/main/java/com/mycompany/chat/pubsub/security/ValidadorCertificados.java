package com.mycompany.chat.pubsub.security;

import java.io.FileInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Classe responsavel por validar os certificados:
 * valida se o certificado do Broker foi assinado pela CA (no meu caso o professor fictio)
 * valida se o certificado do cliente foi assinado pelo broker.
 * 
 * A cadeia de confiança é:
 * Professor/CA -> Broker -> Cliente
 * @author Carol
 */
public class ValidadorCertificados {
    public static final String PASTA =
        "C:/Users/Carol/Documents/Redes de Computadores II/chat-pubsub(Atualizado)/chat-pubsub/certificados";
    public static final String CAMINHO_CA = PASTA + "/ca.crt";
    public static final String CAMINHO_BROKER = PASTA + "/bruno.crt";
    
    /**
     * valida se o certificado do broker é confiavel
     * carrega o broker.cert e verifica se a assinatura dele foi feita pela
     * chave privada correspondente a chave publica da CA
     * @return 
     */
    public static boolean validarCertificadoBroker() {
        try {
            X509Certificate certificadoCA = carregarCertificado(CAMINHO_CA);
            X509Certificate certificadoBroker = carregarCertificado(CAMINHO_BROKER);
            /**
             * Verifica se o certificado do broker foi assinado pela chave privada
             * correspondente ao ca.crt
             */
            certificadoBroker.verify(certificadoCA.getPublicKey());
            certificadoBroker.checkValidity();
            
            System.out.println("Certificado validado com sucesso.");
            System.out.println("Broker autorizado: " + certificadoBroker.getSubjectX500Principal());
            return true;

        } catch (Exception e){
            System.out.println("Erro ao validar certificado do Broker.");
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * valida se o certificado do cliente é valido:
     * primeiro valida o cert do broker, dps usa a chave publica do broker
     * para verificar se o cert do cliente foi realmente assinado pelo broker
     * @param certificadoCliente
     * @return 
     */
    public static boolean validarCertificadoUsuario(CertificadoCliente certificadoCliente){
        try {
            if (certificadoCliente == null){
                System.out.println("Certificado do cliente vazio");
                return false;
            }
            
            if (certificadoCliente.getNomeUsuario() == null
                    || certificadoCliente.getNomeUsuario().isBlank()
                    || certificadoCliente.getChavePublicaUsuarioBase64() == null
                    || certificadoCliente.getChavePublicaUsuarioBase64().isBlank()
                    || certificadoCliente.getAssinaturaBrokerBase64() == null
                    || certificadoCliente.getAssinaturaBrokerBase64().isBlank()){
                System.out.println("Certificado incompleto.");
                return false;
            }
            
            if (!validarCertificadoBroker()){
                System.out.println("Certificado invalido. Nao eh possivel validar cliente.");
                return false;
            }
                        
            PublicKey chavePublucaBroker = carregarChavePublicaBroker();
            boolean assinaturaValida = CryptoUtil.verificarAssinatura(certificadoCliente.dadosParaAssinar(),certificadoCliente.getAssinaturaBrokerBase64(), chavePublucaBroker);
            
            System.out.println("Certificado do cliente valido? "+ assinaturaValida);
            
            return assinaturaValida;
            
        } catch (Exception e){
            System.out.println("Erro ao validar certificado do cliente.");
            return false;
        }
    }
    
    public static PublicKey carregarChavePublicaBroker() throws Exception {
        X509Certificate certificadoBroker = carregarCertificado(CAMINHO_BROKER);
        return certificadoBroker.getPublicKey();
    }

    public static X509Certificate carregarCertificado(String caminho) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(caminho)){
            return (X509Certificate) factory.generateCertificate(fis);
        } 
    }
}
