import java.net.InetAddress;
import java.util.UUID;

public class Mensagem {
    private String arquivo;
    private InetAddress ipOrigem;
    private Integer portaOrigem;
    private String id;
    private String tipo;

    public Mensagem(String arquivo, InetAddress ipOrigem, Integer portaOrigem){
        this.id = UUID.randomUUID().toString();
        this.ipOrigem = ipOrigem;
        this.portaOrigem = portaOrigem;
        this.arquivo = arquivo;
        this.tipo = "SEARCH";
    }

    public Mensagem(String arquivo, InetAddress ipOrigem, Integer portaOrigem, String id){
        this.arquivo = arquivo;
        this.ipOrigem = ipOrigem;
        this.portaOrigem = portaOrigem;
        this.id = id;
        this.tipo = "RESPONSE";
    }

    public String getArquivo() {
        return arquivo;
    }

    public InetAddress getIpOrigem() {
        return ipOrigem;
    }

    public Integer getPortaOrigem() {
        return portaOrigem;
    }

    public String getId() {
        return id;
    }

    public String getTipo(){
        return tipo;
    }
}
