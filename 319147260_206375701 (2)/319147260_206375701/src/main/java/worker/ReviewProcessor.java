package worker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;



public class ReviewProcessor {

    public static String processReviews(String jsonString) {

        String receivedMessage = jsonString;
        String[] parts = receivedMessage.split("spliter");
        String inputName = parts[0];
        String blockNumber = parts[1];
        String text = parts[2];

        JsonArray reviewsArray = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("reviews");

        StringBuilder resultBuilder = new StringBuilder();

        sentimentAnalysisHandler sentimentAnalysis = new sentimentAnalysisHandler();
        namedEntityRecognetion namedEntitys = new namedEntityRecognetion();

        for (int i = 0; i < reviewsArray.size(); i++) {
            JsonObject review = reviewsArray.get(i).getAsJsonObject();
            String reviewText = review.get("text").getAsString();
            int rating = review.get("rating").getAsInt();
            String link = review.get("link").getAsString();

            int sentimentScore = sentimentAnalysis.findSentiment(reviewText);

            String entities = namedEntitys.extractEntities(reviewText);

            if (entities.isEmpty()) {
                entities = "Not Found";
            }
            boolean isSarcasm = isSarcasm(sentimentScore, rating);

            String sentimentColor = getColorForSentiment(sentimentScore);

            String coloredLinkText = "<span style=\"color: " + sentimentColor + "\">" + link + "</span>";

            String reviewResult = "Review " + (i + 1) + ": "
                    + "Link: " + coloredLinkText + ", "
                    + "Entities: [" + entities + "], Sarcasm Detected: " + isSarcasm + "<br>" + "\n";

            resultBuilder.append(reviewResult);
        }
        String finalResult = inputName + "spliter" + blockNumber + "spliter" + resultBuilder.toString();

        return finalResult;
    }

    private static boolean isSarcasm(int sentimentScore, int rating) {
        return Math.abs(sentimentScore-rating)>=3;
    }

    // Get the color based on sentiment score
    private static String getColorForSentiment(int sentimentScore) {
        if (sentimentScore == 0) {
            return "darkred"; // very negative
        } else if (sentimentScore == 1) {
            return "red"; // negative
        } else if (sentimentScore == 2) {
            return "black"; // neutral
        } else if (sentimentScore == 3) {
            return "lightgreen"; // positive
        } else {
            return "darkgreen"; // very positive
        }
    }
}