import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Cliente {
    private String nombre;
    private String ip;
    private int puerto;
    private Cliente clienteAnterior;
    private Cliente clienteSiguiente;

    private static Map<String, Cliente> clientes = new HashMap<>();

    public Cliente(String archivoConfig, String archivoConex, String nombre) {
        this.nombre = nombre;
        cargarConfiguracion(archivoConfig);
        establecerClientesAnteriorYPosterior(archivoConex);
        iniciarPrograma();
    }

    public Cliente(String nombre, String ip, int puerto,Cliente clienteAnterior,Cliente clienteSiguiente) {
        this.nombre = nombre;
        this.ip = ip;
        this.puerto = puerto;
        this.clienteAnterior = clienteAnterior;
        this.clienteSiguiente = clienteSiguiente;
    }

    private void agregarCliente(String nombre, Cliente cliente) {
        clientes.put(nombre, cliente);
    }

    public Cliente pasarAtributos(String[] atributos) {
        if (atributos.length == 3) {
            int puerto = Integer.parseInt(atributos[2]);
            return new Cliente(atributos[0], atributos[1], puerto, null, null);
        }
        return null;
    }

    public void establecerClientesAnteriorYPosterior(String connectionFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(connectionFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] nombresClientes = line.split("<->");
                for (int i = 0; i < nombresClientes.length; i++) {
                    int indiceAnterior = -1;
                    if (i > 0) {
                        indiceAnterior = i - 1;
                    }

                    int indiceSiguiente = -1;
                    if (i < nombresClientes.length - 1) {
                        indiceSiguiente = i + 1;
                    }
                    Cliente clienteActual = clientes.get(nombresClientes[i]);
                    if (indiceAnterior != -1) {
                        Cliente clienteAnterior = clientes.get(nombresClientes[indiceAnterior]);
                        clienteActual.setClienteAnterior(clienteAnterior);
                    } else {
                        clienteActual.setClienteAnterior(null);
                    }

                    if (indiceSiguiente != -1) {
                        Cliente clienteSiguiente = clientes.get(nombresClientes[indiceSiguiente]);
                        clienteActual.setClienteSiguiente(clienteSiguiente);
                    } else {
                        clienteActual.setClienteSiguiente(null);
                    }

                    clientes.put(clienteActual.getNombre(), clienteActual);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cargarConfiguracion(String configFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length == 3) {
                    Cliente esteCliente = pasarAtributos(partes);
                    agregarCliente(esteCliente.getNombre(), esteCliente);
                } else {
                    System.out.println("Error en el formato de la línea: " + linea );
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void iniciarPrograma() {
        try {

            ServerSocket serverSocket = new ServerSocket(clientes.get(this.getNombre()).getPuerto());

            System.out.println("Escuchando en " + clientes.get(this.getNombre()).getIp() + ":" + clientes.get(this.getNombre()).getPuerto());

            new Thread(() -> {
                try {
                    while (true)
                    {
                        Socket socket = serverSocket.accept();
                        BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        new Thread(() -> escucharMensajes(entrada)).start();
                    }
                } catch (IOException e) {
                    System.err.println("Error al escuchar conexiones: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Ingrese destinatario: ");
                String destinatario = scanner.nextLine();

                if (clientes.containsKey(destinatario) &&  !(destinatario.equals(this.getNombre()))) {
                    System.out.print("Ingrese mensaje: ");
                    String mensaje = scanner.nextLine();
                    enviarMensaje(destinatario, mensaje);
                } else {
                    System.out.println("Destinatario invalido.");
                }
            }
        } catch (IOException e) {
            System.err.println("Error al escuchar conexiones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void comunicacionAtras(Cliente clienteOrigen, Cliente clienteDestino, String mensaje) {
        Cliente este = clientes.get(clienteOrigen.getNombre());
        Cliente clienteIntermediario = este.getClienteAnterior();

        while (clienteIntermediario != null && !clienteIntermediario.equals(clienteDestino)) {
            if (!clienteIntermediario.equals(clienteDestino.getClienteAnterior()) && !clienteIntermediario.equals(clienteDestino.getClienteSiguiente())) {
                enviarMensajeDirecto(clienteIntermediario.getClienteSiguiente(),clienteIntermediario, mensaje);
                System.out.println("REENVIANDO A: " + clienteIntermediario.getNombre() + " PARA QUE LLEGUE A: " + clienteDestino.getNombre() + ": " + clienteOrigen.getNombre() + " | " + mensaje);
            }
            clienteIntermediario = clienteIntermediario.getClienteAnterior();
        }

        if (clienteIntermediario != null || clienteIntermediario.equals(clienteDestino)) {
            enviarMensajeDirecto(clienteIntermediario.getClienteSiguiente(),clienteDestino, mensaje);
        }
    }
    private void comunicacionSiguiente(Cliente clienteOrigen, Cliente clienteDestino, String mensaje) {
        Cliente este = clientes.get(clienteOrigen.getNombre());
        Cliente clienteIntermediario = este.getClienteSiguiente();

        while (clienteIntermediario != null && !clienteIntermediario.equals(clienteDestino)) {
            if (!clienteIntermediario.equals(clienteDestino.getClienteAnterior())) {
                enviarMensajeDirecto(clienteIntermediario.getClienteAnterior(),clienteIntermediario, mensaje);
                System.out.println("REENVIANDO A " + clienteIntermediario.getNombre() + " PARA QUE LLEGUE A: " + clienteDestino.getNombre()  + ": " + clienteOrigen.getNombre() + " | " + mensaje);
            }
            clienteIntermediario = clienteIntermediario.getClienteSiguiente();
        }

        if (clienteIntermediario != null || clienteIntermediario.equals(clienteDestino)) {
            enviarMensajeDirecto(clienteIntermediario.getClienteAnterior(), clienteDestino, mensaje);
        }
    }

    private void enviarMensajeDirecto(Cliente remitente,Cliente clienteDestino, String mensaje) {
        remitente = clientes.get(remitente.getNombre());
        try (Socket socketDestino = new Socket(clienteDestino.getIp(), clienteDestino.getPuerto());
             PrintWriter salidaDestino = new PrintWriter(socketDestino.getOutputStream(), true)) {

            salidaDestino.println( remitente.getNombre() + " : "  + mensaje);
            System.out.println("Mensaje enviado a: " + clienteDestino.getNombre() );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void enviarMensaje(String destinatario, String mensaje) {
        Cliente esteCliente;
        esteCliente = clientes.get(this.getNombre());
        Cliente clienteDestino = clientes.get(destinatario);

        if (clienteDestino != null) {
            if (clienteDestino.equals(this.getClienteSiguiente()) || clienteDestino.equals(this.getClienteAnterior())) {
                enviarMensajeDirecto(esteCliente,clienteDestino, mensaje);
            } else {
                reenviarMensaje(esteCliente,clienteDestino,mensaje);
            }
        } else {
            System.out.println("Destinatario no encontrado.");
        }
    }
    private void reenviarMensaje(Cliente clienteOrigen, Cliente clienteDestino, String mensaje) {
        clienteOrigen = clientes.get(clienteOrigen.getNombre());
        clienteDestino = clientes.get(clienteDestino.getNombre());


        List<Cliente> listaClientes = new ArrayList<>(clientes.values());
       // funcion para ordenar el index, 0/1/2/3
        listaClientes.sort(Comparator.comparingInt(c -> indexOfClient("C:\\Users\\augus\\IdeaProjects\\TRABAJOREDES\\src\\Conexion", c.getNombre())));

        int indiceOrigen = listaClientes.indexOf(clienteOrigen);
        int indiceDestino = listaClientes.indexOf(clienteDestino);

        if (indiceDestino > indiceOrigen) { // izq a derecha
            comunicacionSiguiente(clienteOrigen, clienteDestino, mensaje);
        }
        else if (indiceDestino < indiceOrigen) { // derecha a izq
            comunicacionAtras(clienteOrigen, clienteDestino, mensaje);
        } else {
            System.out.println(" Error: Son iguales. ");
        }
    }
    private int indexOfClient(String connectionFile, String clienteNombre) {
        try (BufferedReader br = new BufferedReader(new FileReader(connectionFile))) {
            String linea;
            int index = 0;
            while ((linea = br.readLine()) != null) {
                String[] nombresClientes = linea.split("<->");
                for (String nombre : nombresClientes) {
                    if (nombre.equalsIgnoreCase(clienteNombre)) {
                        return index;
                    }
                    index++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }


    private void escucharMensajes(BufferedReader entrada) {
        try {
            while (true) {
                String mensajeRecibido = entrada.readLine();
                if (mensajeRecibido != null) {
                    if (mensajeRecibido.contains("a traves de intermediario/s")) {
                        System.out.println("Mensaje recibido a traves de intermediario.");
                    } else {
                        System.out.println("Mensaje recibido: " + mensajeRecibido);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public Cliente getClienteAnterior() {
        return clienteAnterior;
    }

    public void setClienteAnterior(Cliente clienteAnterior) {
        this.clienteAnterior = clienteAnterior;
    }

    public Cliente getClienteSiguiente() {
        return clienteSiguiente;
    }

    public void setClienteSiguiente(Cliente clienteSiguiente) {
        this.clienteSiguiente = clienteSiguiente;
    }

    public static Map<String, Cliente> getClientes() {
        return clientes;
    }

    public static void setClientes(Map<String, Cliente> clientes) {
        Cliente.clientes = clientes;
    }


}