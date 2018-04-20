package me.dougclarknc.bakery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.grpc.internal.IoUtils;

@MultipartConfig(maxFileSize = 10 * 1024 * 1024, // max size for uploaded files
		maxRequestSize = 20 * 1024 * 1024, // max size for multipart/form-data
		fileSizeThreshold = 5 * 1024 * 1024) // start writing to Cloud Storage
												// after 5MB
@SuppressWarnings("serial")
@WebServlet(name = "Review", urlPatterns = { "/review" })
public class ReviewServlet extends HttpServlet {

	DatastoreService datastore;
	GcsService gcsService;
	private final String BUCKET = "dougclarknc-bread.appspot.com";
	private static final int BUFFER_SIZE = 2 * 1024 * 1024;
	Storage storage;

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		Gson gson = new Gson();
		String keyString = request.getParameter("key");


		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> reviewContent = gson.fromJson(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);
				
		List<Entity> reviews = new ArrayList<Entity>();
		Map<String, String[]> reviewMap = new HashMap<String, String[]>();

		if (reviewContent.isEmpty()) {
			Query q  = new Query("Review").setFilter(new FilterPredicate("date", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			reviews = pq.asList(FetchOptions.Builder.withDefaults());
		} else {
			Key key = KeyFactory.stringToKey(keyString);
			try {
				reviews.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (Entity e : reviews) {
			String[] data = {
				(String)e.getProperty("date"),
				(String)e.getProperty("comment"),
				(String)e.getProperty("recipieKey"),
				(String)e.getProperty("reviewerKey"),
				(String)e.getProperty("productTitle"),
				(String)e.getProperty("rating"),
				(String)e.getProperty("pictureURL")
			};
			reviewMap.put(KeyFactory.keyToString(e.getKey()), data);
		}

		String jsonRes = gson.toJson(reviewMap);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		Gson gson = new Gson();
		
		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> reviewContent = gson.fromJson(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);

		String keyString = reviewContent.get("key");
		Entity review = null;
		if (keyString == null) {
			review = new Entity("Recipe");
		} else {
			try {
				review = datastore.get(KeyFactory.stringToKey(keyString));
			} catch (EntityNotFoundException enfe) {
				out.print(enfe.toString());
			}
		}

		review.setProperty("imageURL",
					"https://storage.googleapis.com/" + BUCKET + "/" + review.getKey().toString() + "/image.jpg");
		review.setProperty("date", new Date().getTime());
		review.setProperty("comment", reviewContent.get("reviewContent_comment"));
		review.setProperty("recipeKey", reviewContent.get("reviewContent_recipeKey"));
		review.setProperty("reviewerKey", reviewContent.get("reviewContent_reviewerKey"));

		try {
			datastore.put(review);

			// Send user to confirmation page with personalized confirm text
			String confirmation = 
				"Review with date " + review.getProperty("date") 
			+ " and comment " + review.getProperty("comment") 
			+ " and recipeID " + review.getProperty("recipeID")
			+ " and reviewerID " + review.getProperty("ReviewerID")
			+ " and rating " + review.getProperty("rating")
			+ " and pictureURL " + review.getProperty("pictureURL")
			+ " and key " + review.getKey().toString() + " created.";

			out.println(confirmation);
		} catch (DatastoreFailureException dsfe) {
			out.println("Failure: " + dsfe.toString());
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
		gcsService = GcsServiceFactory.createGcsService(new RetryParams.Builder().initialRetryDelayMillis(10)
				.retryMaxAttempts(10).totalRetryPeriodMillis(150000).build());
		storage = StorageOptions.getDefaultInstance().getService();
	}

	private String storeImage(String fileName, Part filePart) throws IOException {
		BlobId blobId = BlobId.of(BUCKET, fileName);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(filePart.getContentType()).build();
		InputStream inputStream = filePart.getInputStream();
		byte[] file = IoUtils.toByteArray(inputStream);
		Blob blob = storage.create(blobInfo, file);
		return blob.toString();
	}

	private void copy(InputStream input, OutputStream output) throws IOException {
		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead = input.read(buffer);
			while (bytesRead != -1) {
				output.write(buffer, 0, bytesRead);
				bytesRead = input.read(buffer);
			}
		} finally {
			input.close();
			output.close();
		}
	}
}
