package com.mycompany.chat.pubsub.broker;

import com.mycompany.chat.pubsub.model.Mensagem;
import com.mycompany.chat.pubsub.model.Topico;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

/**
 * Controla topicos existentes, clientes online, quem participa do topico,
 * controla solicitacoes, armazena historico de msgs, armazenas msgs pendentes,
 * repassa pacotes de chave e notifica clientes sobre mudanças em tempo real
 * -- NAO sabe a chave real dos topicos
 * 
 * @author caroline.jesus
 */
public class Broker {
    private final Map<String, Topico> topicos = new ConcurrentHashMap<>();
    private final Map<String, ClienteHandler> clientesOnline = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> inscritosPorTopico = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> topicosPorUsuario = new ConcurrentHashMap<>();
    private final Map<String, List<Mensagem>> mensagensPendentes = new ConcurrentHashMap<>();
    private final Map<String, List<Mensagem>> historicoPorTopico = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> indiceInicioPorUsuarioTopico = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> solicitacoesPorTopico = new ConcurrentHashMap<>();
    private final Map<String, String> chavesPublicasUsuarios = new ConcurrentHashMap<>();
    private final Map<String, List<JSONObject>> pacotesChavesPendentes = new ConcurrentHashMap<>();

    
    //guarda usuarios online
    public void registrarCliente(String nomeUsuario, ClienteHandler cliente) {
        clientesOnline.put(nomeUsuario, cliente);

        System.out.println("Cliente online " + nomeUsuario);
        System.out.println("Total online: " + clientesOnline.size());
    }

    //Remove clientes da lista de usuarios online
    public void removerCliente(String nomeUsuario) {
        clientesOnline.remove(nomeUsuario);

        System.out.println("Cliente offline " + nomeUsuario);
    }

    //cria um topico novo ja com o criador, evita topicos sem membros
    public boolean criarTopicoComCriador(String nomeTopico, String nome) {
        if (nomeTopico == null || nomeTopico.isBlank()) {
            return false;
        }

        if (topicos.containsKey(nomeTopico)) {
            return false;
        }

        Topico topico = new Topico(nomeTopico);

        topicos.put(nomeTopico, topico);

        inscritosPorTopico.computeIfAbsent(nomeTopico, k -> ConcurrentHashMap.newKeySet()).add(nome);
        topicosPorUsuario.computeIfAbsent(nome, k -> ConcurrentHashMap.newKeySet()).add(nomeTopico);

        solicitacoesPorTopico.computeIfAbsent(nomeTopico, k -> ConcurrentHashMap.newKeySet());

        int tamanhoHistoricoAtual = historicoPorTopico.getOrDefault(nomeTopico, Collections.emptyList()).size();

        indiceInicioPorUsuarioTopico.computeIfAbsent(nome, k -> new ConcurrentHashMap<>()).put(nomeTopico, tamanhoHistoricoAtual);

        notificarAtualizacao();
        notificarAtualizacaoUsuario(nomeTopico);

        return true;
    }
    
    //deve ser chamado dps de solicitacao de entrada for aprovada
    public boolean inscreverUsuario(String nomeUsuario, String nomeTopico) {
        if (!topicos.containsKey(nomeTopico)) {
            return false;
        }

        inscritosPorTopico.computeIfAbsent(nomeTopico, k -> ConcurrentHashMap.newKeySet()).add(nomeUsuario);

        topicosPorUsuario.computeIfAbsent(nomeUsuario, k -> ConcurrentHashMap.newKeySet()).add(nomeTopico);

        int tamanhoHistoricoAtual = historicoPorTopico.getOrDefault(nomeTopico, Collections.emptyList()).size();

        indiceInicioPorUsuarioTopico.computeIfAbsent(nomeUsuario, k -> new ConcurrentHashMap<>()).put(nomeTopico, tamanhoHistoricoAtual);

        System.out.println(nomeUsuario + " entrou em " + nomeTopico
                + " a partir da mensagem " + tamanhoHistoricoAtual);

        notificarAtualizacaoUsuario(nomeTopico);

        return true;
    }
    
    public void desinscreverUsuario(String nomeUsuario, String nomeTopico) {
        Set<String> usuarios = inscritosPorTopico.get(nomeTopico);

        if (usuarios != null) {
            usuarios.remove(nomeUsuario);
        }

        Set<String> topicosUsuario = topicosPorUsuario.get(nomeUsuario);

        if (topicosUsuario != null) {
            topicosUsuario.remove(nomeTopico);
        }

        Map<String, Integer> indicesUsuario = indiceInicioPorUsuarioTopico.get(nomeUsuario);

        if (indicesUsuario != null) {
            indicesUsuario.remove(nomeTopico);
        }

        Set<String> restantes = inscritosPorTopico.get(nomeTopico);

        if (restantes == null || restantes.isEmpty()) {
            removerTopicoSeVazio(nomeTopico);
            return;
        }

        notificarAtualizacaoUsuario(nomeTopico);
    }
    
    //exclui o topico caso ele esteja vazio
    public void removerTopicoSeVazio(String nome) {
        Set<String> usuarios = inscritosPorTopico.get(nome);

        if (usuarios == null || usuarios.isEmpty()) {
            topicos.remove(nome);
            inscritosPorTopico.remove(nome);
            solicitacoesPorTopico.remove(nome);
            historicoPorTopico.remove(nome);

            notificarAtualizacao();
        }
    }
    
    public boolean usuarioEstaInscrito(String nomeUsuario, String nomeTopico) {
        Set<String> noTopico = this.topicosPorUsuario.get(nomeUsuario);

        return noTopico != null && noTopico.contains(nomeTopico);
    }
    
    /** envia msgs para todos 
     * nesse modelo a msg é mandada criptografada onde o broker armazena e repassa
     */
    public void enviarMensagem(Mensagem msg) {
        historicoPorTopico.computeIfAbsent(msg.getTopico(), k -> new CopyOnWriteArrayList<>()).add(msg);

        Set<String> inscritos = inscritosPorTopico.getOrDefault(msg.getTopico(), Collections.emptySet());

        System.out.println("Publicando em: " + msg.getTopico());
        System.out.println("Inscritos no topico: " + inscritos);
        System.out.println("Clientes online: " + clientesOnline.keySet());

        for (String nomeUsuario : inscritos) {
            ClienteHandler clienteOnline = clientesOnline.get(nomeUsuario);

            if (clienteOnline != null) {
                clienteOnline.enviarMensagem(msg);
            } else {
                mensagensPendentes.computeIfAbsent(nomeUsuario, k -> new CopyOnWriteArrayList<>()).add(msg);
            }
        }
    }
    
    //entrega msgs armazenadas no buffer qnd o online fica online
    public void entregarMensagensPendentes(String nomeUsuario, ClienteHandler cliente) {
        List<Mensagem> pendentes = mensagensPendentes.remove(nomeUsuario);

        if (pendentes == null || pendentes.isEmpty()) {
            return;
        }

        System.out.println("Entregando " + pendentes.size()
                + " mensagens pendentes para " + nomeUsuario);

        for (Mensagem msg : pendentes) {
            cliente.enviarMensagem(msg);
        }
    }
    
    //retorna o historico de um topico a partir do id que o user entrou
    public List<Mensagem> getHistoricoTopicoParaUsuario(String nomeUsuario, String nomeTopico) {
        List<Mensagem> historico = historicoPorTopico.getOrDefault(nomeTopico, Collections.emptyList());

        Map<String, Integer> indicesUsuarios = indiceInicioPorUsuarioTopico.get(nomeUsuario);

        if (indicesUsuarios == null || !indicesUsuarios.containsKey(nomeTopico)) {
            return Collections.emptyList();
        }

        int indiceInicio = indicesUsuarios.get(nomeTopico);

        if (indiceInicio < 0) {
            indiceInicio = 0;
        }

        if (indiceInicio > historico.size()) {
            indiceInicio = historico.size();
        }

        return new ArrayList<>(historico.subList(indiceInicio, historico.size()));
    }
    
    //nao inscreve o ususario no topico mas cria uma solicitacao pra outro usuario autorizar
    public boolean solicitarEntradaTopico(String nomeUsuario, String nomeTopico) {
        if (!topicos.containsKey(nomeTopico)) {
            return false;
        }

        if (usuarioEstaInscrito(nomeUsuario, nomeTopico)) {
            return false;
        }

        solicitacoesPorTopico.computeIfAbsent(nomeTopico, k -> ConcurrentHashMap.newKeySet()).add(nomeUsuario);
        System.out.println(nomeUsuario + " solicitou entrada no topico " + nomeTopico);

        notificarSolicitacoesTopico(nomeTopico);

        return true;
    }
    
    //dps que aprova um usuario o broker envia a chave do topico 
    public boolean aprovarEntradaTopico(String aprovador, String usuarioSolicitante, String nomeTopico) {
        if (!usuarioEstaInscrito(aprovador, nomeTopico)) {
            return false;
        }

        Set<String> solicitacoes = solicitacoesPorTopico.get(nomeTopico);

        if (solicitacoes == null || !solicitacoes.contains(usuarioSolicitante)) {
            return false;
        }

        solicitacoes.remove(usuarioSolicitante);

        boolean inscrito = inscreverUsuario(usuarioSolicitante, nomeTopico);

        if (inscrito) {
            System.out.println(aprovador + " aprovou " + usuarioSolicitante + " no topico " + nomeTopico);

            notificarSolicitacoesTopico(nomeTopico);
            notificarAtualizacaoUsuario(nomeTopico);

            solicitarEnvioChaveTopico(aprovador, usuarioSolicitante, nomeTopico);
        }

        return inscrito;
    }

    public boolean recusarEntrada(String aprovador, String usuarioSolicitante, String nomeTopico) {
        if (!usuarioEstaInscrito(aprovador, nomeTopico)) {
            return false;
        }

        Set<String> solicitacoes = solicitacoesPorTopico.get(nomeTopico);

        if (solicitacoes == null || !solicitacoes.contains(usuarioSolicitante)) {
            return false;
        }

        solicitacoes.remove(usuarioSolicitante);

        System.out.println(aprovador + " recusou " + usuarioSolicitante + " no topico " + nomeTopico);
        notificarSolicitacoesTopico(nomeTopico);

        return true;
    }
    
    //envia lista de solicitacoes para os usuarios online do topico
    public void notificarSolicitacoesTopico(String nomeTopico) {
        JSONObject json = new JSONObject();
        JSONArray listaSolicitacoes = new JSONArray();

        Set<String> solicitacoes = solicitacoesPorTopico.getOrDefault(nomeTopico, Collections.emptySet());

        for (String usuario : solicitacoes) {
            listaSolicitacoes.add(usuario);
        }

        json.put("type", "ACCESS_REQUEST_LIST");
        json.put("topic", nomeTopico);
        json.put("requests", listaSolicitacoes);

        Set<String> participantes = inscritosPorTopico.getOrDefault(nomeTopico, Collections.emptySet());

        for (String participante : participantes) {
            ClienteHandler cliente = clientesOnline.get(participante);

            if (cliente != null) {
                cliente.enviarJson(json);
            }
        }

    }
    
    //guarda chave publica do usuario no broker para quando o aprovador for aprovar alguem no topico
    //ele consegue buscar a chave no broker
    public void registrarChavePublicaUsuario(String nomeUsuario, String chavePublica64) {
        chavesPublicasUsuarios.put(nomeUsuario, chavePublica64);

        System.out.println("Chave puvlica registrada de: " + nomeUsuario);
    }
    
    //solicita a chave pra um membro que vai aprovar um pendente, aí ele envia a chave
    //ele apenas informa a chave publica do usuario aprovado
    private void solicitarEnvioChaveTopico(String aprovador, String usuarioAprovado, String nomeTopico) {
        ClienteHandler clienteAprovador = clientesOnline.get(aprovador);

        if (clienteAprovador == null) {
            return;
        }

        String chavePublicaAprovado = chavesPublicasUsuarios.get(usuarioAprovado);

        if (chavePublicaAprovado == null) {
            System.out.println("Chave publica nao encontrada");
            return;
        }

        JSONObject json = new JSONObject();

        json.put("type", "SEND_TOPIC_KEY_REQUEST");
        json.put("topic", nomeTopico);
        json.put("approvedUser", usuarioAprovado);
        json.put("approvedUserPublicKey", chavePublicaAprovado);

        clienteAprovador.enviarJson(json);

        System.out.println("solicitacao enviada para " + usuarioAprovado);
    }
    
    //repassa um pacote de chave criptografado, se o destino estiver online entrega na hora,
    //se estiver off guarda p entregar no proximo login
    public void repassarPacoteChave(String usarioDestino, JSONObject pacote){
        ClienteHandler clienteDestino = clientesOnline.get(usarioDestino);
        
        if(clienteDestino != null){
            clienteDestino.enviarJson(pacote);
            System.out.println("pacote entregue para " + usarioDestino);
            return;
        }
        
        pacotesChavesPendentes.computeIfAbsent(usarioDestino, k -> new CopyOnWriteArrayList<>()).add(pacote);
        System.out.println("usuario offline. pacote guardado p entrega posterior");
    }
    
    //entrega chaves guardadas
    public void entregarPacotesDeChavesPendentes(String nomeUsuario, ClienteHandler cliente) {
        List<JSONObject> pendentes = pacotesChavesPendentes.remove(nomeUsuario);
        
        if( pendentes == null || pendentes.isEmpty()){
            return;
        }
        
        for (JSONObject pacote : pendentes){
            cliente.enviarJson(pacote);
        }
    }
    
    //metodo auxiliar que ajuda a manter a lista de topicos atualizada em tempo real
    public void notificarAtualizacao() {
        JSONObject json = new JSONObject();
        JSONArray arrayTopicos = new JSONArray();

        for (String nomeTopico : listarTopicos()) {
            arrayTopicos.add(nomeTopico);
        }

        json.put("type", "TOPIC_LIST");
        json.put("topics", arrayTopicos);

        System.out.println("Notificando clientes sobre topicos: " + json.toJSONString());
        System.out.println("Total clientes conectados: " + clientesOnline.size());

        for (ClienteHandler cliente : clientesOnline.values()) {
            cliente.enviarJson(json);
        }
    }
    
    public void notificarAtualizacaoUsuario(String nomeTopico) {
        JSONObject json = new JSONObject();
        JSONArray participantesArray = new JSONArray();

        Set<String> participantes = inscritosPorTopico.getOrDefault(nomeTopico, Collections.emptySet());

        for (String participante : participantes) {
            participantesArray.add(participante);
        }
        json.put("type", "PARTICIPANT_LIST");
        json.put("topic", nomeTopico);
        json.put("participants", participantesArray);

        for (String nomeUsuario : participantes) {
            ClienteHandler cliente = clientesOnline.get(nomeUsuario);

            if (cliente != null) {
                cliente.enviarJson(json);
            }
        }
    }
    
    //usado quando TOPIC_LIST é enviado
    public Set<String> listarTopicos() {
        return topicos.keySet();
    }
    
    public Set<String> listarParticipantes(String nomeTopico) {
        return inscritosPorTopico.getOrDefault(nomeTopico, Collections.emptySet());
    }
    
    public Topico getTopico(String nome) {
        return topicos.get(nome);
    }

    //salva historico de msgs a partir do momento em que o usuario entra no topico
    public List<Mensagem> getHistoricoTopcio(String nomeTopico) {
        return historicoPorTopico.getOrDefault(nomeTopico, Collections.emptyList());
    }

    public Set<String> listarSolicitacoes(String nomeTopico) {
        return solicitacoesPorTopico.getOrDefault(nomeTopico, Collections.emptySet());
    }

    //retorna chave p de um usuario
    public String getChavePublicaUsuario(String nomeUsuario) {
        return chavesPublicasUsuarios.get(nomeUsuario);
    }
    
    ClienteHandler getClienteOnline(String nomeUsuario) {
        return clientesOnline.get(nomeUsuario);
    }
}
