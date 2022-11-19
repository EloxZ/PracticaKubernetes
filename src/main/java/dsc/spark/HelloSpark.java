package dsc.spark;
import static spark.Spark.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;
import spark.Request;


public class HelloSpark {
	private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST","localhost");

    public static void main(String[] args) {
        get("/hello", (req, res) -> "Hello World");
        get("/nuevo/:dato", (req, res) -> nuevoDato(req, res));
        get("/listar", (req, res) -> listarDatos(req, res));
        get("/listajson", (req, res) -> listarJson(req, res));
        get("/limpiar", (req, res) -> limpiar(req, res));
        get("/grafica", (req, res) -> mostrarGrafica(req, res));
    }

    // Recoge hasta 10 datos más recientes y los muestra en una gráfica,
    // que se actualizará llamando a listajson.
    private static String mostrarGrafica(Request req, spark.Response res) {
        String resString = "No se puede mostrar la grafica.";

        try {
            Jedis jedis = new Jedis(REDIS_HOST);
            ArrayList<Tuple> data = new ArrayList<>();

            long size = jedis.llen("queue#datos");
            int start = 0;
            if (size > 10) {
                start = (int) size - 10;
            }
            
			for (long i=start; i<size; i++) {
                String fecha = jedis.lindex("queue#fechas",i);
				String dato = jedis.lindex("queue#datos",i);
                data.add(new Tuple(fecha, Double.parseDouble(dato)));
            }

            jedis.close();
            resString = GraficaChart.crearGrafica(data);

        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
        }

        return resString;
    }

    // Añade un dato junto a su marca de tiempo en la base de datos,
    // si el dato está especificado y es numérico.
    private static String nuevoDato(Request req, spark.Response res) {
        String dato = req.params("dato");
        try {
            Long.parseLong(dato);
        } catch (Exception e) {
            // Dato no numérico.
            dato = null;
        }
        String resString = "Dato agregado satisfactoriamente.";
        if (dato != null) {
            try {
                Jedis jedis = new Jedis(REDIS_HOST);
                DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        
                jedis.rpush("queue#fechas", df.format(new Date()));
                jedis.rpush("queue#datos", dato);

                jedis.close();
            } catch (Exception e) {
                e.printStackTrace();
                resString = "Fallo al almacenar el dato.";
                res.status(500);
            }
        } else {
            resString = "Dato no especificado.";
            res.status(400);
        }

        return resString;
    }

    // Limpia los valores de la base de datos.
    private static String limpiar(Request req, spark.Response res) {
        String resString = "Base de datos limpiada correctamente.";

        try {
            Jedis jedis = new Jedis(REDIS_HOST);
            jedis.flushAll();
            jedis.close();
    
        } catch (Exception e) {
            e.printStackTrace();
            resString = "Fallo al limpiar base de datos.";
            res.status(500);
        }

        return resString;
    }

    // Lista todos los datos.
    private static String listarDatos(Request req, spark.Response res) {
        String resString = "Fallo al consultar datos.";

        try {
            Jedis jedis = new Jedis(REDIS_HOST);
            StringBuilder sb = new StringBuilder();
            sb.append("MEDICION - FECHA\r\n\r\n");

            long size = jedis.llen("queue#datos");
			for (long i=0; i<size; i++) {
                String fecha = jedis.lindex("queue#fechas",i);
				String dato = jedis.lindex("queue#datos",i);
                sb.append(dato + " - " + fecha + "\r\n");
            }

            resString = sb.toString();
            res.type("text/plain");
            jedis.close();

        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
        }

        return resString;
    }

    // Devuelve un JSON con los hasta 10 datos más recientes.
    private static String listarJson(Request req, spark.Response res) {
        String resString = "Fallo al consultar datos.";

        try {
            Jedis jedis = new Jedis(REDIS_HOST);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"Mediciones\":[");

            long size = jedis.llen("queue#datos");
            int start = 0;

            if (size > 10) {
                start = (int) size - 10;
            }

			for (long i=start; i<size; i++) {
                String fecha = jedis.lindex("queue#fechas",i);
				String dato = jedis.lindex("queue#datos",i);
                sb.append( "{\"time\": \"" + fecha + "\", \"valor\": " + dato + "},");
            }

            sb.deleteCharAt(sb.length()-1);
            sb.append("]}");
            resString = sb.toString();
            res.type("application/json; charset=utf-8");
            jedis.close();

        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
        }

        return resString;
    }
}
