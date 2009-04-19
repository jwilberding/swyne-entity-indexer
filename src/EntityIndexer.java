import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.regex.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.openrdf.model.*;
import org.openrdf.util.jdbc.*;
import org.openrdf.repository.*;
import org.openrdf.repository.*;
import org.openrdf.vocabulary.RDFS;

import org.wikipedia.miner.model.*;
import org.wikipedia.miner.util.*;
import org.wikipedia.miner.annotation.preprocessing.*;
import org.wikipedia.miner.annotation.preprocessing.PreprocessedDocument.RegionTag;

public class EntityIndexer{

   private static final String WIKIPEDIA_DBSERVER;
   private static final String WIKIPEDIA_DB;
   private static final String WIKIPEDIA_DBUSER;
   private static final String WIKIPEDIA_DBPASS;

   private static final String DISAMBIGUATOR_MODEL;
	private double defaultMinProbability = 0.5 ;
	private boolean defaultShowTooltips = false ;

   private URI SOMETHING_URI;
   private URI ISVALIDURI;
   private URI ARTICLEURI;
   private URI PERSONURI;
   private URI ORGANIZATIONURI;
   private URI LOCATIONURI;
   private URI APPEARSINURI;
   private URI SUBCLASSOF;
   private URI CLASS;
   private URI LABEL;
   private URI PROPERTY;
   private URI TYPE;
   

   private Repository repo;

   private Wikipedia wikipedia;
   private TopicDetector topicDetector;
   private Disambiguator disambiguator;
   private TextProcessor tp;
   private AbstractSequenceClassifier classifier;

   public EntityIndexer(String url, String user, String password, String server, String repoId, 
         AbstractSequenceClassifier asc)
   {
      repo = new HTTPRepository(server,repoId);
      repo.initialize();
      RepositoryConnection rc = repo.getRepositoryConnection();
      ValueFactory vf = rc.getValueFactory();

      SOMETHING_URI = vf.createURI("SOMETHING");
      ISVALIDURI =  vf.createURI("ISVALID");
      ARTICLEURI = vf.createURI("NEWSARTICLE");
      PERSONURI = vf.createURI("PERSON");
      ORGANIZATIONURI = vf.createURI("ORGANIZATION");
      LOCATIONURI = vf.createURI("LOCATION");
      APPEARSINURI = vf.createURI("APPEARSIN");

      SUBCLASSOF = vf.createURI(RDFS.SUBCLASSOF);
      CLASS = vf.createURI(RDFS.CLASS);
      LABEL = vf.createURI(RDFS.LABEL);
      TYPE = vf.createURI(RDF.TYPE);
      PROPERTY = vf.createURI(RDF.PROPERTY);
      

      wikipedia = new Wikipedia(WIKIPEDIA_DBSERVER, WIKIPEDIA_DB, 
            WIKIPEDIA_DBUSER, WIKIPEDIA_DBPASS);
      disambiguator = new Disambiguator(wikipedia, tp, 0.01, 0, 25) ;
      disambiguator.loadClassifier(new File(DISAMBIGUATOR_MODEL));

      topicDetector = new TopicDetector(wikipedia, disambiguator, null, 
            new SortedVector<RegionTag>(), false);

      /* SOMETHING IS A CLASS */
      conn.add(SOMETHINGURI, TYPE, CLASS);
      /* PERSON IS A SUBCLASS OF SOMETHING */
      conn.add(PERSONURI, SUBCLASSOF, SOMETHINGURI);
      /* ORGANIZATION IS A SUBCLASS OF SOMETHING */
      conn.add(ORGANIZATIONURI, SUBCLASSOF, SOMETHINGURI);
      /* LOCATION IS A SUBCLASS OF SOMETHING */
      conn.add(LOCATIONURI, SUBCLASSOF, SOMETHINGURI);
      /* ARTICLE IS A SUBCLSAS OF SOMETHING */
      conn.add(ARTICLEURI, SUBCLASSOF, SOMETHINGURI);
      /* APPEARS IN IS A PROPERTY */
      conn.add(APPEARSIN, TYPE, PROPERTY);

      classifier = asc;

   }

   /*Returns the xml document representing the entity*/
   private SortedVector<Topic> disambiguate(String article)
   {
      PreprocessedDocument doc = new PreprocessedDocument(article, article, "", null, null);
      
      SortedVector<Topic> allTopics = linkDetector.getWeightedTopics(topicDetector.getTopics(doc, null));
      SortedVector<Topic> bestTopics = new SortedVector<Topic>();

      for (Topic t:allTopics) {
         if (t.getWeight() >= minProbability)
            bestTopics.add(t, true) ;

         detectedTopics.add(t, true) ;
      }

      return detectedTopics;

   }

   /*Returns true if the entity represented by this uri is in the rdf database*/
   private boolean isIndexed(String uri, RepositoryConnection conn)
   {
      ValueFactory vf = conn.getValueFactory();
      return conn.hasStatement(vf.createURI(uri), ISVALIDURI, SOMETHING_URI, false);

   }

   /*Build the rdf for the entity and store it*/
   private void buildEntities(String article, String article_uri, String article_title)
   {
      SortedVector<Topic> topics = disambiguate(article);
      Vector<NEREntity> finalTopics = intersectWithNER(article, topics);
      RepositoryConnection conn = repo.getRepositoryConnection(); 
      URI articleURI = conn.getValueFactory().createURI(article_uri);
      Literal articleTitle = conn.getValueFactory().createLiteral(articleTitle);

      conn.add(articleURI, ISVALIDURI, ARTICLEURI);
      conn.add(articleURI, LABEL, articleTitle);

      for(NEREntity n:finalTopics) {
         InfoBox ib = buildInfoBox(n.topic);
         buildRdf(n.topic.getId().toString(), n.topic, articleURI, ib, n.type, conn);
         
      }

   }

   private Vector<NEREntity> intersectWithNER(String article, SortedVector<Topic> topics)
   {
      List<List<CoreLabel>> documents = classifier.classify(article);
      SortedVector<TopicReference> references = resolveCollisions(topics);
      HashMap<Integer,Topic> topicsById = new HashMap<Integer, Topic>();
      HashMap<Integer,NEREntity> entitiesByIndex = new HashMap<Integer,NEREntity>();
      HashSet<Integer> doneTopics = new HashSet<Integer>();
      Vector<NEREntity> intersection = new SortedVector<NEREntity>();

      for (Topic topic: topics) 
			topicsById.put(topic.getId(), topic) ;

      for(List<CoreLabel> doc:documents){
         for(CoreLabel classified:doc){
            String word = classified.word();
            String type = classified.getString(AnswerAnnotation.class);
            int index = article.indexOf(word,classified.index());
            
            if(index != classified.index())
               throw new RuntimeException("index="+index+" classified.index()="+classified.index());

            NEREntity entity = new NEREntity(word,type,index,null);
            entitiesByIndex.put(index,entity);
         }
      }

      for(TopicReference ref:references){
         int start = ref.getPosition().getStart();
         int end = ref.getPosition().getEnd();
         int id  = ref.getTopicId();
         
         if(doneTopics.contains(id))
            continue;

         Topic t = topicsById.get(id);
         NEREntity entity = entitiesByIndex.get(start);
         if(entity == null)
            throw new RuntimeException("Cannot find entity at index " + start + ":" + t);

         doneTopics.add(id);
         entity.topic = t;
         intersection.add(entity);

      }
      return intersection;
   }

   private Vector<TopicReference> resolveCollisions(Collection<Topic> topics) {

      HashMap<Integer, Double> topicWeights = new HashMap<Integer, Double>() ;

      TreeSet<TopicReference> temp = new TreeSet<TopicReference>() ;

      for(Topic topic: topics) {	
         for (Position pos: topic.getPositions()) {
            topicWeights.put(topic.getId(), topic.getWeight()) ;

            TopicReference tr = new TopicReference(null, topic.getId(), pos) ;
            temp.add(tr) ;
         }
      }

      Vector<TopicReference> references = new Vector<TopicReference>() ;
      references.addAll(temp) ;

      for (int i=0 ; i<references.size(); i++) {
         TopicReference reference = references.elementAt(i) ;

         Vector<TopicReference> overlappedTopics = new Vector<TopicReference>() ;

         for (int j=i+1 ; j<references.size(); j++){
            TopicReference reference2 = references.elementAt(j) ;

            if (reference.overlaps(reference2)) 
               overlappedTopics.add(reference2) ;
         }

         for (int j=0 ; j<overlappedTopics.size() ; j++) {
            references.removeElementAt(i+1) ;
         }
      }
      return references;
   }

   private void buildRDF(String uri, String label, Article article, URI newsArtURI, InfoBox ibox, int entityType,
         RepositoryConnection conn)
   {
      URI typeURI;
      ValueFactory vf = conn.getValueFactory();
      URI entityURI = vf.createURI(uri);

      if(entityType == NEREntity.PERSON)
         typeURI = PERSONURI;
      else if(entityType == NEREntity.ORGANIZATION)
         typeURI = ORGANIZATIONURI;
      else if(entityType == NEREntity.PLACE)
         typeURI = PLACEURI;

      conn.add(entityURI, APPEARSINURI, newsArtURI);
      conn.add(entityURI, ISVALIDURI, SOMETHINGURI);
      conn.add(entityURI, TYPE, typeURI);

      if(isIndexed(uri,conn))
         return;

      
      if(ibox != null){
         ArrayList<Field> fields = ibox.getFields();
         String boxlabel = ibox.getLabel();
         URI box_uri = vf.createURI(ibox.getURI());

         /* Fix. Find link in wikipedia in future */
         if(box_uri == null)
            box_uri = "" + wikipedia.getMostLikelyArticle(label,tp);

         conn.add(box_uri, SUBCLASSOF, typeURI);
         conn.add(box_uri, LABEL, vf.createLiteral(boxlabel));
         conn.add(entityURI,TYPE, box_uri);
         conn.add(entityURI,LABEL,vf.createLiteral(label));

         if(fields != null) {
            for(Field f:fields){
               FVal val = f.getVal();
               String val_label = val.getLabel();
               String val_link = val.getLink();

               String field_label = f.getLabel();
               String field_uri = f.getURI();

               URI fieldURI;
               URI valURI;

               if(val_link != null){
                  valURI = vf.createURI("" + wikipedia.getArticleByTitle(val_link).getId());

                  if(field_uri == null)
                     fieldURI = vf.createURI(field_label);
                  else
                     fieldURI = vf.createURI("" + wikipedia.getArticleByTitle(field_uri).getId());

                  /* CHECK THE FOLLOWING TO MAKE SURE ITS RIGHT */
                  conn.add(fieldURI, TYPE, PROPERTY);
                  conn.add(fieldURI, LABEL, vf.createLiteral(field_label));

                  /* Should we recursively build the entities? */
                  conn.add(entityURI, fieldURI, valURI);
                  conn.add(valURI, LABEL, vf.createLiteral(val_link));

               }

            }
         }

      }

   }

   private int getType(String term)
   {
      return 0;
   }

   private InfoBox buildInfoBox(Article article)
   {
      String infobox_regex = "\\{\\{Infobox [^(}})*";
     // String 
      Pattern infobox_pattern = Pattern.compile(regex, MULTILINE);
      Matcher matches = infobox_pattern.matcher(article.getContent());


   }

   /* Pass in untagged article */
   public void indexArticle(String article, String article_name, String article_uri)
   {
      buildEntities(article, article_uri, article_name);

   }

   public static class NEREntity
   {
      public static final int PERSON = 1;
      public static final int PLACE = 2;
      public static final int ORGANIZATION = 3;
      
      public Topic topic;
      public int type;
      public int start;
      public String word;


      public NEREntity(String word, String type, int start, Topic topic)
      {
         this.word = word;
         if(type == "ORGANIZATION"){
            this.type = NEREntity.ORGANIZATION;
         }else if(type == "PERSON"){
            this.type = NEREntity.PERSON;
         }else if(type == "LOCATION"){
            this.type = NEREntity.PLACE;
         }else
            throw new RuntimeException("Got " + type);
         this.start = start;
         this.topic = topic;
      }
   }
}
