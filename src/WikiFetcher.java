
import ru.ispras.sedna.driver.*;
import java.util.*;

public class WikiFetcher {

   private SednaConnection con;
   private String url = "localhost";  
   private String dbname = "wiki";  
   private String user = "SYSTEM";  
   private String password = "MANAGER";

   public WikiFetcher() throws Exception {

       try{  
         con = DatabaseManager.getConnection(url, dbname, user, password);
         con.begin();
       }catch(Exception e){

       }

   }

   public ArrayList fetchWikis(String title)
   {
       ensureOpen();
       SednaStatement st1 = con.createStatement();
       boolean call_res = st1.execute("index-scan('article-by-title','"+ title +",'EQ'");
       ArrayList aList = new ArrayList(20);


       if(call_res)
       {
          SednaSerializedResult res = st1.getSerializedResult();
          String itr = null;
          ArrayList results = new ArrayList(50);

          /* Sedna Requires sequential access to results. Thus, we cannot execuate multiple statements
           * and choose the order which results are fetched. Possible memory problems here.
           */
          while((itr = itr.next()) != null)
          {
            results.add(itr);   
          }

          Iterator iterator = results.iterator();
          while(iterator.hasNext()){
             itr = (String)iterator.next();
             
             if(isRedirect(itr)){
               /* Get the redirect. For example Obama redirects to Barrack Obama */
               ArrayList wikis = fetchWikis(getRedirect(itr));
               aList.addAll(wikis);
             }else if(isDisambiguation(itr)){
                /* Add all disambiguations to the list */
                String[] s = getDisambiguations(itr);
                for(int i = 0; i < s.length; i++){
                   ArrayList wikis = fetchWikis(s[i]);
                   aList.addAll(wikis);
                }
             }else{
                aList.add(itr);
                System.err.println(itr);
             }

          }

       }

       return aList;

   }

   public String fetchAbstractByTitle(String title)
   {

   }

   public String fetchAbstractByURI(String uri)
   {

   }

   private boolean ensureOpen()
   {
      if(!con.isOpen())
         con = DatabaseManager.getConnection(url,dbname,uesr,password);
      return con.isOpen();
   }

   private boolean isRedirect(String s)
   {

   }

   private String getRedirect(String s)
   {

   }

   private boolean isDisambiguation(String s)
   {

   }

   private String[] getDisambiguations(String s)
   {

   }

}
