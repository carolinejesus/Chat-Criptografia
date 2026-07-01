package com.mycompany.chat.pubsub.security;

import java.security.PublicKey;

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
    public static final String PASTA = System.getProperty("user.dir") + "/certificados";
    
    /**
     * valida se o certificado do broker é confiavel
     * carrega o broker.cert e verifica se a assinatura dele foi feita pela
     * chave privada correspondente a chave publica da CA
     * @return 
     */
    public static boolean validarCertificadoBroker() {
        try {
            CertificadoBroker certificadoBroker = (CertificadoBroker) ArquivoCertificadoUtil.carregarObjeto(PASTA + "/broker.cert");
            PublicKey chavePublicaProfessor =
                    (PublicKey) ArquivoCertificadoUtil.carregarObjeto(
                            PASTA + "/professor_public.key"
                    );
            
            if(certificadoBroker.getNomeBroker() == null
                    || certificadoBroker.getNomeBroker().isBlank()
                    || certificadoBroker.getChavePublicaBrokerBase64() == null
                    || certificadoBroker.getChavePublicaBrokerBase64().isBlank()
                    || certificadoBroker.getAssinaturaProfessorBase64() == null
                    || certificadoBroker.getAssinaturaProfessorBase64().isBlank()){
                System.out.println("Certificado do broker incompleto.");
                return false;
            }
            
            String dadosAssinados = certificadoBroker.dadosParaAssinar();
            boolean assinaturaValida = CryptoUtil.verificarAssinatura(dadosAssinados, certificadoBroker.getAssinaturaProfessorBase64(), chavePublicaProfessor);
            
            if(!assinaturaValida){
                System.out.println("Assinatura invalida.");
                return false;
            }
            
            System.out.println("Certificado validado com sucesso.");
            System.out.println("Broker autorizado: " + certificadoBroker.getNomeBroker());
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
            
            CertificadoBroker certificadoBroker = (CertificadoBroker) ArquivoCertificadoUtil.carregarObjeto(PASTA + "/broker.cert");
            
            PublicKey chavePublucaBroker = CryptoUtil.base64ParaChavePublica(certificadoBroker.getChavePublicaBrokerBase64());
            boolean assinaturaValida = CryptoUtil.verificarAssinatura(certificadoCliente.dadosParaAssinar(),certificadoCliente.getAssinaturaBrokerBase64(), chavePublucaBroker);
            
            System.out.println("Certificado do cliente valido? "+ assinaturaValida);
            
            return assinaturaValida;
            
        } catch (Exception e){
            System.out.println("Erro ao validar certificado do cliente.");
            return false;
        }
    }
}
