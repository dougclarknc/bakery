package me.dougclarknc.bakery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

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
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.gson.Gson;

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

		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> reviewContent = request.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("reviewContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));
		Entity review = null;
		Key key = KeyFactory.stringToKey(reviewContent.get("reviewContent_key"));
		try {
			review = datastore.get(key);
		} catch (EntityNotFoundException e) {
			e.printStackTrace();
		}

		String jsonRes = gson.toJson(review);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> reviewContent = request.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("reviewContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));

		Key key = KeyFactory.stringToKey(reviewContent.get("reviewContent_key"));
		Entity review = null;
		try {
			review = datastore.get(key);
			review.setProperty("rating", reviewContent.get("reviewContent_rating"));

			Part filePart = request.getPart("file");
			if (filePart != null) {
				storeImage(review.getKey().toString(), filePart);
			}

			review.setProperty("image",
					"https://storage.googleapis.com/" + BUCKET + "/" + review.getKey().toString() + "/image.jpg");
		} catch (EntityNotFoundException e) {
			review = new Entity("Review");
		}

		review.setProperty("date", new Date().getTime());
		review.setProperty("comment", reviewContent.get("reviewContent_comment"));
		review.setProperty("recipe", reviewContent.get("reviewContent_recipe"));
		review.setProperty("reviewer", reviewContent.get("reviewContent_reviewer"));

		try {
			datastore.put(review);

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Review with key " + review.getKey().toString() + " created.";

			request.setAttribute("confirmation", confirmation);
			request.getRequestDispatcher("/confirm.jsp").forward(request, response);
		} catch (DatastoreFailureException dsfe) {
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
