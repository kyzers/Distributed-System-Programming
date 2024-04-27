package worker;



import java.util.Properties;
import java.util.List;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class namedEntityRecognetion {

   
    Properties props;
    StanfordCoreNLP NERPipeline;

    public namedEntityRecognetion() {
        props = new Properties();
        props.put("annotators", "tokenize , ssplit, pos, lemma, ner");
        NERPipeline = new StanfordCoreNLP(props);
    }
    
    public String extractEntities(String review) {
        StringBuilder entitiesString = new StringBuilder();

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(review);
        // run all Annotators on this text
        NERPipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        
        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String ne = token.get(NamedEntityTagAnnotation.class);
                // Filter for PERSON, LOCATION, and ORGANIZATION entities
                if (ne.equals("PERSON") || ne.equals("LOCATION") || ne.equals("ORGANIZATION")) {
                    entitiesString.append(word).append(" [").append(ne).append("], ");
                }
            }
        }
        // Remove the trailing comma and space, if any
        if (entitiesString.length() > 0) {
            entitiesString.setLength(entitiesString.length() - 2);
        }
        return entitiesString.toString();
    }


}
