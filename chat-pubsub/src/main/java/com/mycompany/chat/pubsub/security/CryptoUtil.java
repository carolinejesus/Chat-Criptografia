package com.mycompany.chat.pubsub.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Classe que gera par de chaves RSA, converte chave publica para Base64 e vice
 * versa, assina docs com chave privada, verifica assinatura com chave publica,
 * criptografa a msg com chave do topico com RSA, gera chave AES, criptografa e
 * decriptografa mensagens com AES.
 *
 * Usada no lado do cliente e no servidor também
 *
 * @author caroline.jesus
 */
public class CryptoUtil {

    /**
     * Gera um par de chaves RSA. A chave publica pode ser compartilhada, a
     * chave privada deve ficar protegida no cliente ou no servidor.
     *
     * @return
     * @throws Exception
     */
    public static KeyPair gerarParChaves() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        return gerador.generateKeyPair();
    }

    /**
     * Converte uma chave publica RSA para texto Base64 isso permite enviar a
     * chave em JSON ou salvar em certificado
     *
     * @param chavePublica
     * @return
     */
    public static String chavePublicaParaBase64(PublicKey chavePublica) {
        return Base64.getEncoder().encodeToString(chavePublica.getEncoded());
    }

    /**
     * Converte uma chave publica em Base64 de volta para PublicKey BASE
     * TRANSFORMA BYTES EM TEXTO
     *
     * @param chaveBase64
     * @return
     * @throws Exception
     */
    public static PublicKey base64ParaChavePublica(String chaveBase64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(chaveBase64);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(spec);
    }

    /**
     * Assina dados usando uma chave privada RSA Usado para gerar assinaturas de
     * certificados
     *
     * @param dados
     * @param chavePrivada
     * @return
     * @throws Exception
     */
    public static String assinar(String dados, PrivateKey chavePrivada) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");

        signature.initSign(chavePrivada);
        signature.update(dados.getBytes(StandardCharsets.UTF_8));

        byte[] assinatura = signature.sign();

        return Base64.getEncoder().encodeToString(assinatura);
    }

    /**
     * Verifica se uma assinatura foi feita pela chave privada correspondente a
     * chave publica informada
     *
     * @param dados
     * @param assinaturaBase64
     * @param chavePublica
     * @return
     * @throws Exception
     */
    public static boolean verificarAssinatura(String dados, String assinaturaBase64, PublicKey chavePublica) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");

        signature.initVerify(chavePublica);
        signature.update(dados.getBytes(StandardCharsets.UTF_8));

        byte[] assinatura = Base64.getDecoder().decode(assinaturaBase64);

        return signature.verify(assinatura);
    }

    //===========
    //CRIPTOGRAFIA DE CHAVE DE TOPICO
    // ============
    /**
     * Criptografa um texto usando uma chave publica RSA é usada para
     * criptografar a chave AES do topico antes de enviar para um usuario
     * aprovado
     *
     * @param texto
     * @param chavePublica
     * @return
     * @throws Exception
     */
    public static String criptografarComChavePublica(String texto, PublicKey chavePublica) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");

        cipher.init(Cipher.ENCRYPT_MODE, chavePublica);

        byte[] textoCriptografado = cipher.doFinal(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(textoCriptografado);
    }

    /**
     * Descriptografa um texto usando uma chave privada RSA é usado pelo usuario
     * aprovado para abrir a chave AES do topico que foi criptografado com a
     * chave publica dele
     *
     * @param textoCriptografado64
     * @param chavePrivada
     * @return
     * @throws Exception
     */
    public static String descriptografarComChavePrivada(String textoCriptografado64, PrivateKey chavePrivada) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");

        cipher.init(Cipher.DECRYPT_MODE, chavePrivada);

        byte[] textoCriptografado = Base64.getDecoder().decode(textoCriptografado64);

        byte[] textoOriginal = cipher.doFinal(textoCriptografado);

        return new String(textoOriginal, StandardCharsets.UTF_8);
    }

    //================
    // AES (criptografica simetrica: msm chave usada para criptografar e decriptografar) - Chaves e msgs
    //=================
    /**
     * Gera uma chave AES em Base64 essa chave é usada para criptografar msgs
     * dentro de um topico
     *
     * @return
     * @throws Exception
     */
    public static String gerarChaveAESBase64() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        SecretKey chave = keyGenerator.generateKey();

        return Base64.getEncoder().encodeToString(chave.getEncoded());
    }

    /**
     * criptografa uma msg usando AES/CGM o metodo gera um IV aleatorio para
     * cada msg e retorna: IV_BASE64:CIFRADO_BASE64 GCM AJUDA A DETECTAR
     * ALTERAÇOES NO CONTEUDO
     *
     * @param texto
     * @param chaveBase64
     * @return
     * @throws Exception
     */
    public static String criptografarAES(String texto, String chaveBase64) throws Exception {
        SecretKey chave = new SecretKeySpec(Base64.getDecoder().decode(chaveBase64), "AES");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);

        cipher.init(Cipher.ENCRYPT_MODE, chave, spec);

        byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));

        String ivBase64 = Base64.getEncoder().encodeToString(iv);
        String cifradoBase64 = Base64.getEncoder().encodeToString(cifrado);

        return ivBase64 + ":" + cifradoBase64;
    }

    /**
     * Descriptografa uma msg AES/GCM no formato: IV_BASE64:CIFRADO_BASE64
     *
     * @param textoCifrado
     * @param chaveBase64
     * @return
     * @throws Exception
     */
    public static String descriptografarAES(String textoCifrado, String chaveBase64) throws Exception {
        SecretKey chave = new SecretKeySpec(Base64.getDecoder().decode(chaveBase64), "AES");

        String[] partes = textoCifrado.split(":");

        if (partes.length != 2) {
            throw new Exception("deu erro na msg com cifra");
        }

        byte[] iv = Base64.getDecoder().decode(partes[0]);
        byte[] cifrado = Base64.getDecoder().decode(partes[1]);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);

        cipher.init(Cipher.DECRYPT_MODE, chave, spec);

        byte[] original = cipher.doFinal(cifrado);

        return new String(original, StandardCharsets.UTF_8);
    }

    public static PrivateKey carregarChavePrivada(String caminho) throws Exception {
        byte[] bytesArquivo = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(caminho));

        String conteudoTexto = new String(bytesArquivo, StandardCharsets.UTF_8);

        byte[] bytesChave;

        if (conteudoTexto.contains("-----BEGIN PRIVATE KEY-----")) {
            conteudoTexto = conteudoTexto.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            bytesChave = Base64.getDecoder().decode(conteudoTexto);
        } else {
            bytesChave = bytesArquivo;
        }

        java.security.spec.PKCS8EncodedKeySpec spec
                = new java.security.spec.PKCS8EncodedKeySpec(bytesChave);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePrivate(spec);

    }
}
