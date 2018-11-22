

import java.io.* ;
import java.net.* ;
import java.net.Socket;
import java.util.*;


public final class WebServer
{
    public static Map<String,String> mimesTypes = new HashMap<>(); //Hier werden später die MimeTypes gespeichert
    // static damit von unserem Thread welcher in einer anderen Klasse ist drauf zugegriffen werden kann

    public static void main(String args[]) throws Exception
    {
        ServerSocket socket = new ServerSocket(6789);           //festlegen der Portnummer und öffnen des Sockets
        FileInputStream mimesInput = null;

        String mimesFilename = "./src/mime.types";                 //Das ist der Defaul Path sollte nicht über -mime ein anderer festgelegt werden

        if(args.length>=2){
            if(args[0].equals("-mime")){
                mimesFilename = args[1];
            }
        }

        try {
            mimesInput = new FileInputStream(mimesFilename);
            readMimesIntoHashmap(mimesInput);
            mimesInput.close();                                  //Unsere Hashmap ist gefüllt, also können wi
        } catch (FileNotFoundException e) {
            return;                                              //Wenn keine mime Datei geöffnet werden konnte dann kann der Webserver nicht funktionieren und wird deshalb einfach wieder geschlossen
        }

    while(true) {                                                  //Dauerschleife welche auf Anfragen an den Server wartet um einen neuen Thread zu erstellen
            HttpRequest request = new HttpRequest(socket.accept());
            Thread thread = new Thread(request);
            thread.start();
        }
    }
    private static void readMimesIntoHashmap(FileInputStream fis) throws Exception{

        BufferedReader buffer = new BufferedReader(new InputStreamReader(fis));
        String line = null;
        String mimeType = null;
        String extension = null;
        for(int i = 0;i<27;i++){
            line = buffer.readLine(); //Die ersten 27 Zeilen werden einfach durchgegangen weil sie keine
                                             //hilfreichen informationen enthalten
        }

        while((line = buffer.readLine())!=null){                        //Das Dokument Zeile für Zeile einlesen, auch die leeren Zeilen
            if(line.length()!=0) {                                      //Bei leeren Zeilen kann der Tokenizer nicht arbeiten deshalb werden diese hier rausgenommen
                StringTokenizer tokens = new StringTokenizer(line);     //Tokenizer unterteilt die Zeile. Das erste Element wird mit allen
                mimeType = tokens.nextToken();                          //folgenden Elementen zusammen in eine Hashmap gepackt
                while (tokens.hasMoreTokens()) {                        //es werden nur die Mime Types eingelesen, denen eine extension zugeordnet ist
                    extension = tokens.nextToken();                        //weil nur zur hashmap hinzugefügt wird wenn es mehr als ein token pro zeile gibt
                    mimesTypes.put(extension, mimeType);
                }
            }

        }

    }

}

class HttpRequest implements Runnable{

    private Socket socket = null;

    public HttpRequest(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            processHttpRequest();
        }catch(Exception e){
            return;                 //wenn der prozess nicht gestartet werden kann soll abgebrochen werden
        }
    }

    public void processHttpRequest() throws Exception {

        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String requestline = br.readLine();


        br.readLine();                              //erste Zeile wird übersprungen, da steht nur unsere Addresse (localhost:6789)
        String UserBrowserData = null;
        String Method = "";                       //leere Strings erzeugen um sie unten benutzen zu können. Sie werden im try block gefüllt
        String filesource = "";                   //im catch fall terminiert der thread also wird es nie zu fehlern kommen weil die werte null sind
        String version = "";
        String filename = "";
        String headerLine = "";
        String contentLength ="";
        String requestEntityBody = "";
        int contenLengthInt = 0;


        try {
            UserBrowserData = br.readLine();                //in der zweiten Zeile stehen die Informationen über den Browser des Users welche wir bei der 404 Nachricht mit ausgeben wollen, daher speichern wir diese
            StringTokenizer tokens = new StringTokenizer(requestline);
            Method = tokens.nextToken();             //am Anfang der requestline steht immer die Methode also die Art der request
            filesource = tokens.nextToken();
            version = tokens.nextToken();
            filename = "." + filesource;
            List<String> headerLines = new ArrayList<String>();

            if(Method.equals("POST")) {

                while ((headerLine = br.readLine()).length() != 0) {         //Hier werden alle headerlines eingelesen und gespeichert
                    headerLines.add(headerLine);
                }

                for(String Entry : headerLines){                             //Dann wird überprüft, ob es eine Angabe über Content-Length gibt
                    if(Entry.startsWith("Content-Length:")){                 //Wenn ja dann wird diese ausgelesen und es werden so viele Symbole aus dem entityBody ausgelesen
                        contentLength = Entry.split(" ")[1];              //Laut 7.2.2 und 10.4 benötigt jede request mit entityBody eine angegebene Content-Length
                    }                                                        //Wenn es keine gibt nehmen wir also automatisch an, dass es keinen entityBody gibt den wir zu berücksichtigen haben.
                }
                if(!contentLength.equals("")) {                               //Wenn doch müssen wir noch den entityBody überprüfen indem wir Conten-length anzahl an zeichen auslesen und
                    contenLengthInt = Integer.parseInt(contentLength);
                    for (int i = 0; i < contenLengthInt; i++) {
                        if (br.ready()) {
                            requestEntityBody += (char) br.read();
                        }                                                   //hier wird der requestEntityBody ausgelesen wenn Content length vorhanden ist
                    }                                                                                                          //leider konnte ich im rfc 1945 keine genaueren informationen zum aufbau des entitybody von POST oder request an sich finden
                    if (requestEntityBody.startsWith("=") || requestEntityBody.endsWith("=") || !requestEntityBody.contains("=")) {  //überprüfen ob sie der Form a=b sind (laut stackoverflow), also muss = enthalten aber darf nicht damit starten oder enden
                        os.writeBytes("HTTP/1.0 400 Bad Request\r\n");                                                      //wenn doch ist der entityBody falsch und es ist ein bad Request
                        os.writeBytes("Content-type: text/html\r\n");
                        os.writeBytes("\r\n");                                                                              //wenn der entityBody richtig ist wird einfach weitergemacht wie bei Head und Get
                        os.writeBytes("<HTML>" +
                                "<HEAD><TITLE>400 Bad Request</TITLE></HEAD><BODY><p>" +
                                "</p><p>400 Bad Request</p><p>Your Request is missing Parameters</p>" +
                                "</BODY></HTML>");
                        br.close();
                        os.close();
                        is.close();
                        return;

                    }
                }
                else{
                    os.writeBytes("HTTP/1.0 400 Bad Request\r\n");                                                      //wenn doch ist der entityBody falsch und es ist ein bad Request
                    os.writeBytes("Content-type: text/html\r\n");
                    os.writeBytes("\r\n");                                                                              //wenn der entityBody richtig ist wird einfach weitergemacht wie bei Head und Get
                    os.writeBytes("<HTML>" +
                            "<HEAD><TITLE>400 Bad Request</TITLE></HEAD><BODY><p>" +
                            "</p><p>400 Bad Request</p><p>Your Request is missing Parameters</p>" +
                            "</BODY></HTML>");
                    br.close();
                    os.close();
                    is.close();
                    return;
                }

            }


        }catch(NoSuchElementException e){                               //Wenn es eine fehlerhafte Request gibt, also etwas fehlt(Methode,version oder dateiname) gibt es einen bad request
            os.writeBytes("HTTP/1.0 400 Bad Request\r\n");           //In diesem Fall wird 400 Bad Request zurückgegeben und der Thread "terminiert"
            os.writeBytes("Content-type: text/html\r\n");
            os.writeBytes("\r\n");
            os.writeBytes("<HTML>" +
                    "<HEAD><TITLE>400 Bad Request</TITLE></HEAD><BODY>" +
                    "<p>400 Bad Request</p><p>Your Request is missing Parameters</p>"+
                    "</BODY></HTML>");
            br.close();
            os.close();
            is.close();
            return;
        }


        String statusLine = null;
        String contentTypeLine = null;
        String entityBody = null;
        boolean fileExists = true;
        FileInputStream fis = null;

        if (Method.equals("HEAD")||Method.equals("GET")||Method.equals("POST")){        //Bis auf einzelheiten ist das Vorgehen bei allen 3 gleich
                                                                                        //Und bei z.b. einem POST 400 Bad Request kommt das Programm erst gar nicht hier hin
            try {
                fis = new FileInputStream(filename);
            } catch (FileNotFoundException e) {
                fileExists = false;
            }

            if (fileExists) {
                statusLine = "HTTP/1.0 200 OK" + "\r\n";
                contentTypeLine = "Content-type:" + getMimeType(filename) + "\r\n";

            } else {
                statusLine = "HTTP/1.0 404 Data not found\r\n";
                contentTypeLine = "Content-type: text/html\r\n";                 //Rückgabe immer eine Html Datei
                entityBody = "<HTML>" +
                        "<HEAD><TITLE>404 Not Found</TITLE></HEAD><BODY>" +
                        "<p>404 Not Found</p><p>Inet Addresse:" + socket.getInetAddress() + "</p><p>"   //Addresse des Users
                        + UserBrowserData +                                                             //Browserdaten des Users
                        "</p></BODY></HTML>";
            }
        }
        else{
            statusLine="HTTP/1.0 501 Not Implemented\r\n";                      //Wenn es sich nicht um GET,HEAD oder POST handelt wird 501 zurückgegeben
            contentTypeLine= "Content-type:text/html\r\n";                      //Es war nichts vorgeschrieben, deshalb lasse ich hier einfach das gleiche wie bei 404 nur mit 501 machen
            entityBody = "<HTML>" +
                    "<HEAD><TITLE>501 Not Implemented</TITLE></HEAD><BODY>" +
                    "<p>501 Not Implemented</p><p>Inet Addresse:" + socket.getInetAddress() + "</p><p>"
                    + UserBrowserData +
                    "</p></BODY></HTML>";

        }

        os.writeBytes(statusLine);
        os.writeBytes(contentTypeLine);
        os.writeBytes("\r\n");

        if(Method.equals("GET")||Method.equals("POST")) {            //Bei HEAD soll kein entityBody ausgegeben werden und auch keine Datei geschickt werden
            if (fileExists) {                                        //Der Rest des codes hier ist ja wie im tutorial
                try {
                    sendBytes(fis,os);
                } catch (Exception e) {

                }
                fis.close();
            } else {
                os.writeBytes(entityBody);
            }
        }
        br.close();
        os.close();
        is.close();
    }

    private static void sendBytes(FileInputStream fis, OutputStream os)
            throws Exception
    {
        // Construct a 1K buffer to hold bytes on their way to the socket.
        byte[] buffer = new byte[1024];
        int bytes = 0;

        // Copy requested file into the socket's output stream.
        while((bytes = fis.read(buffer)) != -1 ) {
            os.write(buffer, 0, bytes);
        }
    }

    private static String getMimeType(String filename){           //um den MimeType zu bestimmen muss man den namen der datei(filename) nehmen
                                                                  //und die endung rausnehmen, also wenn man den string bei . teilt immer der letzte teil.

         String end = null;
         end = filename.split("\\.")[(filename.split("\\.").length)-1];
                                                                  //sobald wir diese haben können wir dann einfach in unserer hashmap den zugehörigen mimetype raussuchen
         return WebServer.mimesTypes.get(end);
    }

}
