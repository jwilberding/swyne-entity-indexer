import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedVector ;
import java.util.Vector ;

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

   public EntityIndexer(String url, String user, String password, String server, String repoId)
   {
      repo = new HTTPRepository(server,repoId);
      repo.initialize();
      RepositoryConnection rc = repo.getRepositoryConnection();
      ValueFactory vf = rc.getValueFactory();
      SOMETHING_URI = vf.createURI("SOMETHING");
      ISVALIDURI =  vf.createURI("ISVALID");
      SUBCLASSOF = vf.createURI(RDFS.SUBCLASSOF);
      CLASS = vf.createURI(RDFS.CLASS);
      LABEL = vf.createURI(RDFS.LABEL);
      TYPE = vf.createURI(RDF.TYPE);

      wikipedia = new Wikipedia(WIKIPEDIA_DBSERVER, WIKIPEDIA_DB, 
            WIKIPEDIA_DBUSER, WIKIPEDIA_DBPASS);
      disambiguator = new Disambiguator(wikipedia, tp, 0.01, 0, 25) ;
      disambiguator.loadClassifier(new File(DISAMBIGUATOR_MODEL));

      topicDetector = new TopicDetector(wikipedia, disambiguator, null, 
            new SortedVector<RegionTag>(), false);

      conn.add(SOMETHINGURI, TYPE, CLASS);
      conn.add(PERSONURI, SUBCLASSOF, SOMETHINGURI);
      conn.add(ORGANIZATIONURI, SUBCLASSOF, SOMETHINGURI);
      conn.add(LOCATIONURI, SUBCLASSOF, SOMETHINGURI);
      conn.add(ARTICLEURI, SUBCLASSOF, SOMETHINGURI);

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
      SortedVector<NEREntity> finalTopics = intersecttopicsWithNER(article, topics);
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

   private SortedVector<NEREntity> intersectWithNER(String article, SortedVector<Topic> topics)
   {

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

   }

   /* Pass in untagged article */
   public void indexArticle(String article, String article_name, String article_uri)
   {
      buildEntities(article, article_uri, article_name);

   }

   public Vector 

   public static class NEREntity
   {
      public static final int PERSON = 1;
      public static final int PLACE = 2;
      public static final int ORGANIZATION = 3;

      public Topic topic;
      public int type;
   }
}
