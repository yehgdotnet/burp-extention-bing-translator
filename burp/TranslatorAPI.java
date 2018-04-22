package burp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

public class TranslatorAPI {

    private static Boolean env_testing = false;

    private static void log(Object data){
        if (env_testing){
            System.out.println(data);
        }
    }
    public static String beautifyjson(String translatedContent){
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(translatedContent);

        return gson.toJson(je);

    }


    public static String Google_getTranslatedContent(String source) {

        StringBuilder output = new StringBuilder();
        String GOOGLE_API_KEY = "";
        String SOURCE_LANG = "vi";
        String GOOGLE_API_HOST = "https://translation.googleapis.com/language/translate/v2?key=" + GOOGLE_API_KEY + "&source=" + SOURCE_LANG + "&target=en";

        try {

            //DefaultHttpClient httpClient = new DefaultHttpClient(); # deprecated
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost postRequest = new HttpPost(
                    GOOGLE_API_HOST);

            postRequest.setHeader(HttpHeaders.HOST,"translation.googleapis.com");

            postRequest.setHeader(HttpHeaders.USER_AGENT,"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE,"application/json;charset=utf-8");

            log(postRequest.toString());

            StringEntity input = new StringEntity("{\"q\": \"" + source + "\"}");


            postRequest.setEntity(input);

            HttpResponse response = httpClient.execute(postRequest);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.toString());
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            log("\r\n[API Server returned]: ");

            String tmp = "";
            while ((tmp = br.readLine()) != null) {
                log(tmp);
                output.append(tmp);

            }

            httpClient.close();

        } catch (IOException e) {

            e.printStackTrace();

        }

        return output.toString();
    }




    public static String getTranslatedContent_URLencoded(String foreignContents, Boolean isJson, String sourceLang, String destLang) {

        StringBuffer output = new StringBuffer();


        String API_Host = "https://www.bing.com/ttranslate?&IG=861766D92BF04A1A8ABF8E6A15FEBB6F&IID=translator.5034.2";

        log("\r\n[API] source language: " + sourceLang);
        log("\r\n[API] dest language: " + destLang);

        try {

            List<NameValuePair> bing_data = new ArrayList<>();
            if (isJson == false){
                bing_data.add(new BasicNameValuePair("text", foreignContents));
            }
            else{
                /*
                FAIL:
                log("[INFO] Escaping JSON for submission: \r\n" + foreignContents);

                if (foreignContents .contains("\\u")){
                    foreignContents  = StringEscapeUtils.unescapeJava(foreignContents );
                    log("[INFO] Flatting JSON not to include backslash u for submission: \r\n" + foreignContents);
                }
                //bing_data.add(new BasicNameValuePair("text", org.apache.commons.text.StringEscapeUtils.escapeJson(foreignContents)));
                //log("[INFO] JSON Escaped data:\r\n"+ org.apache.commons.text.StringEscapeUtils.escapeJson(foreignContents));
                */

                //bing_data.add(new BasicNameValuePair("text", beautifyjson(foreignContents)));


                bing_data.add(new BasicNameValuePair("text", foreignContents));


            }

            bing_data.add(new BasicNameValuePair("from", sourceLang));
            bing_data.add(new BasicNameValuePair("to", destLang));


            // DefaultHttpClient httpClient = new DefaultHttpClient();
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost postRequest = new HttpPost(
                    API_Host);


            postRequest.setHeader(HttpHeaders.USER_AGENT,"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
            postRequest.setHeader(HttpHeaders.CONTENT_TYPE,"application/x-www-form-urlencoded;charset=UTF-8");
            postRequest.setHeader(HttpHeaders.REFERER,"https://www.bing.com/");


            String s = URLEncodedUtils.format(bing_data, "UTF-8");


            log("\r\n[INFO] URLEncodedUtils: " + s);

            s = s.replaceAll("%0A", "%0D%0A");

            log("\r\n[INFO] %0a%0d fix new Line: " + s);

            StringEntity entity = new StringEntity(s, "UTF-8");

            postRequest.setEntity(entity);

            HttpResponse response = httpClient.execute(postRequest);

            BufferedReader br = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            log("\r\n[API Server returned]: \r\n");

            String tmp = "";
            while ((tmp = br.readLine()) != null) {
                log(tmp);
                output.append(tmp);
            }

            httpClient.close();

        } catch (MalformedURLException e) {

            e.printStackTrace();

        } catch (IOException e) {

            e.printStackTrace();

        }

        return output.toString();
    }

    // get only translated content from Bing

    private static String bStripJson(String source){
        String s = source;

        if(s.contains("translationResponse")){
            s = s.replace("{\"statusCode\":200,\"translationResponse\":\"","");
            s = s.replace("{\"statusCode\": 200, \"translationResponse\": \"","");
            s = s.replace("{\" statusCode \": 200,\" translationResponse \":\"","");
            s = s.replace("{\"statusCode\": 200, \"translationResponse\": \"{\" statusCode \": 200,\" translationResponse \":","");
        }
        if (s.contains("&quot;")){
            s = s.replace("&quot;","\"");
        }
        if (s.contains("\"}")){
            // remove the last two
            s = s.substring(0,s.lastIndexOf("\"}"));
        }
        return s;
    }

    public static String bclean(String source, Boolean is_json){

        String orginal = source;
        String s = source;
        Boolean excep = false;

        try{
            if (is_json == true){
                log("\r\n[Unescaping JSON] " + s);
                s = org.apache.commons.text.StringEscapeUtils.unescapeJson(s);
                log("\r\n[Stage - 1 JSON] " + s);
                s =  JSONUtil.unescape(s);
                log("\r\n[Stage - 2 JSON] " + s);
                s = bStripJson(s);
                log("\r\n[Final JSON] " + s);
            }else{
                s = bStripJson(s);
            }
        }catch(java.lang.RuntimeException re){
            excep = true;
            if (is_json == true){
                s = orginal;
                // replace , with new line for readability
                s = s.replaceAll(",",",\r\n");
                s = s.replaceAll("}","\r\n}");
                log("\r\n[Final JSON] " + s);
            }else{
                s = bStripJson(s);
            }
        }
        try{
            if (!excep){
                log("[INFO] beautifying JSON ");
                s = beautifyjson(s);
            }else{
                log("[INFO] Error in converting JSON; skip beautifying process");
            }

        }catch (Exception ignore){

        }


        log("\r\n[Translated Content] " + s);

        return s;
    }



    // get only translated content from Google

    public static String gclean(String source){

        String s = source;

        if(s.contains("\"detectedSourceLanguage\": \"en\"")){
            return "{\"English language. No translation required\"}";
        }
        if (s.contains("&quot;")){
            s = s.replaceAll("&quot;","\"");
        }
        log("\r\n[IN cleaning] " + s);

        s = s.replace("{translations=[{translatedText=","");
        //s = s.substring(0, s.indexOf(", detectedSourceLanguage="));
        return s;
    }




}