package com.mycompany.chat.pubsub.security;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 *
 * @author Carol
 */
public class GeradorBrokerTeste {
    private static final String PASTA = System.getProperty("user.dir") + "/certificados";
    private static final String PROFESSOR_PUBLIC_KEY = PASTA + "/professor_public.key";
    private static final String PROFESSOR_PRIVATE_KEY = PASTA + "/professor_private_key";
    private static final String BROKER_PUBLIC_KEY = PASTA + "/broker_public.key";
    private static final String BROKER_PRIVATE_KEY = PASTA + "/broker_private.key";
    private static final String BROKER_CERT = PASTA + "/broker.cert";
    
    public static void main(String[] args) {
        try {
            ArquivoCertificadoUtil.criarPastaSeNaoExistir(PASTA);

            KeyPair chavesProfessor = carregarOuCriarChavesProfessorTeste();
            KeyPair chavesBroker = carregarOuCriarChavesBroker();

            String nomeBroker = "BrokerPrincipal";

            String chavePublicaBrokerBase64 =
                    CryptoUtil.chavePublicaParaBase64(chavesBroker.getPublic());

            CertificadoBroker certificadoSemAssinatura = new CertificadoBroker(
                    nomeBroker,
                    chavePublicaBrokerBase64,
                    null
            );

            String assinaturaProfessor = CryptoUtil.assinar(
                    certificadoSemAssinatura.dadosParaAssinar(),
                    chavesProfessor.getPrivate()
            );

            CertificadoBroker certificadoAssinado = new CertificadoBroker(
                    nomeBroker,
                    chavePublicaBrokerBase64,
                    assinaturaProfessor
            );

            ArquivoCertificadoUtil.salvarObjeto(BROKER_CERT, certificadoAssinado);

            System.out.println("Certificado do Broker gerado e assinado pelo professor teste.");
            System.out.println("Professor public key: " + PROFESSOR_PUBLIC_KEY);
            System.out.println("Broker private key: " + BROKER_PRIVATE_KEY);
            System.out.println("Broker public key: " + BROKER_PUBLIC_KEY);
            System.out.println("Broker cert: " + BROKER_CERT);

        } catch (Exception e) {
            System.out.println("Erro ao gerar certificado do Broker.");
            e.printStackTrace();
        }
    }

    private static KeyPair carregarOuCriarChavesProfessorTeste() throws Exception {
        boolean existePublica = ArquivoCertificadoUtil.arquivoExiste(PROFESSOR_PUBLIC_KEY);
        
        boolean existePrivada = ArquivoCertificadoUtil.arquivoExiste(PROFESSOR_PRIVATE_KEY);
        
        if (existePublica && existePrivada){
            PublicKey chavePublica = (PublicKey) ArquivoCertificadoUtil.carregarObjeto(PROFESSOR_PUBLIC_KEY);
            PrivateKey chavePrivada = (PrivateKey) ArquivoCertificadoUtil.carregarObjeto(PROFESSOR_PRIVATE_KEY);
            System.out.println("Chaves do professor teste carregadas.");
            
            return new KeyPair(chavePublica, chavePrivada);
        }
        
        KeyPair chaves = CryptoUtil.gerarParChaves();
        
        ArquivoCertificadoUtil.salvarObjeto(PROFESSOR_PUBLIC_KEY, chaves.getPublic());
        ArquivoCertificadoUtil.salvarObjeto(PROFESSOR_PRIVATE_KEY, chaves.getPrivate());
        
        System.out.println("Chaves do professor teste criadas!");
        
        return chaves;
    }

    private static KeyPair carregarOuCriarChavesBroker() throws Exception {
        boolean existePublica =
                ArquivoCertificadoUtil.arquivoExiste(BROKER_PUBLIC_KEY);

        boolean existePrivada =
                ArquivoCertificadoUtil.arquivoExiste(BROKER_PRIVATE_KEY);

        if (existePublica && existePrivada) {
            PublicKey chavePublica =
                    (PublicKey) ArquivoCertificadoUtil.carregarObjeto(BROKER_PUBLIC_KEY);

            PrivateKey chavePrivada =
                    (PrivateKey) ArquivoCertificadoUtil.carregarObjeto(BROKER_PRIVATE_KEY);

            System.out.println("Chaves do Broker carregadas.");

            return new KeyPair(chavePublica, chavePrivada);
        }

        KeyPair chaves = CryptoUtil.gerarParChaves();

        ArquivoCertificadoUtil.salvarObjeto(BROKER_PUBLIC_KEY, chaves.getPublic());
        ArquivoCertificadoUtil.salvarObjeto(BROKER_PRIVATE_KEY, chaves.getPrivate());

        System.out.println("Chaves do Broker criadas.");

        return chaves;
    }
}
