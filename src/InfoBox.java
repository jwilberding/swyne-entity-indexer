public class InfoBox{
   public InfoBox(String label){

   }
   public InfoBox(String label, String uri){

   }

   public ArrayList<Field> getFields(){

   }

   public void add(Field f, FVal v){

   }

   public String getLabel(){

   }

   public String getURI()
   {
      return null;
   }

   public FVal

   public static class Field{
      private String uri;
      private String label;
      private FVal fval;

      public Field(String label, String uri)
      {
         this.uri = uri;
         this.label = label;
      }

      public String getURI()
      {

      }

      public FVal getVal(){

      }

      public String getLabel(){

      }
   }

   public static class FVal{
      private String label;
      private String link;

      public FVal(String label, String link)
      {
         this.label = label;
         this.link = link;
      }

      public String getLabel()
      {
         return this.label
      }

      public String getLink()
      {
         return this.link;
      }
   }

}
