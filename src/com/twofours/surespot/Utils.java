package com.twofours.surespot;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.Base64;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class Utils {
	private static Toast mToast;
	private static final String TAG = "Utils";

	// Fast Implementation
	public static String inputStreamToString(InputStream is) throws IOException {
		String line = "";
		StringBuilder total = new StringBuilder();

		// Wrap a BufferedReader around the InputStream
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));

		// Read response until the end
		while ((line = rd.readLine()) != null) {
			total.append(line);
		}

		// Return full string
		rd.close();
		is.close();
		return total.toString();
	}

	public static byte[] inputStreamToBytes(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		byteBuffer.close();
		inputStream.close();
		return byteBuffer.toByteArray();
	}

	public static String inputStreamToBase64(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		byteBuffer.close();
		inputStream.close();
		return new String(base64Encode(byteBuffer.toByteArray()));
	}

	public static byte[] base64Encode(byte[] buf) {
		return Base64.encode(buf, Base64.NO_WRAP | Base64.URL_SAFE);
	}

	public static byte[] base64Decode(String buf) {
		return Base64.decode(buf, Base64.NO_WRAP | Base64.URL_SAFE);
	}

	public static String getOtherUser(String from, String to) {
		return to.equals(EncryptionController.getIdentityUsername()) ? from : to;
	}

	public static String makePagerFragmentName(int viewId, long id) {
		return "android:switcher:" + viewId + ":" + id;
	}

	public static void makeToast(String toast) {
		if (mToast == null) {
			mToast = Toast.makeText(SurespotApplication.getAppContext(), toast, Toast.LENGTH_SHORT);
		}

		mToast.setText(toast);
		mToast.show();
	}

	public static String getSharedPrefsString(String key) {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		return settings.getString(key, null);
	}

	public static void putSharedPrefsString(String key, String value) {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();
		if (value == null) {
			editor.remove(key);
		}
		else {
			editor.putString(key, value);
		}
		editor.commit();

	}

	public static HashMap<String, Integer> jsonToMap(JSONObject jsonObject) {
		try {
			HashMap<String, Integer> outMap = new HashMap<String, Integer>();

			@SuppressWarnings("unchecked")
			Iterator<String> names = jsonObject.keys();
			while (names.hasNext()) {
				String name = names.next();
				outMap.put(name, jsonObject.getInt(name));
			}

			return outMap;

		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonToMap", e);
		}
		return null;

	}

	public static HashMap<String, Integer> jsonStringToMap(String jsonString) {

		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return jsonToMap(jsonObject);
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringToMap", e);
		}

		return null;

	}

	public static SurespotMessage buildMessage(String to, String mimeType, String plainData, String iv, String cipherData) {
		SurespotMessage chatMessage = new SurespotMessage();
		chatMessage.setFrom(EncryptionController.getIdentityUsername());
		chatMessage.setTo(to);
		chatMessage.setCipherData(cipherData);
		chatMessage.setPlainData(plainData);
		chatMessage.setIv(iv);
		// store the mime type outside teh encrypted envelope, this way we can offload resources
		// by mime type
		chatMessage.setMimeType(mimeType);
		return chatMessage;
	}

	public static void uploadPictureMessage(final Context context, Uri imageUri, final String to, final IAsyncCallback<Boolean> callback) {

		// TODO thread
		Bitmap bitmap = decodeSampledBitmapFromUri(context, imageUri, 480, 240);

		// bitmap.

		// final String data = Utils.inputStreamToBase64(iStream);

		final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 75, jpeg);
		bitmap = null;

		try {
			jpeg.close();
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "uploadPictureMessage", e);
		}

		// Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		EncryptionController.symmetricBase64Encrypt(to, new ByteArrayInputStream(jpeg.toByteArray()), new IAsyncCallback<byte[][]>() {

			@Override
			public void handleResponse(byte[][] results) {
				if (results != null) {
					NetworkController.postFile(context, to, new String(results[0]), results[1], SurespotConstants.MimeTypes.IMAGE,
							new AsyncHttpResponseHandler() {
								public void onSuccess(int statusCode, String content) {
									SurespotLog.v(TAG, "Received picture upload response: " + content);
									callback.handleResponse(true);
								};

								public void onFailure(Throwable error, String content) {
									SurespotLog.v(TAG, "Error uploading picture: " + content);
									callback.handleResponse(false);
								};
							});
				}

			}
		});
		callback.handleResponse(null);
	}

	private static Bitmap decodeSampledBitmapFromUri(Context context, Uri imageUri, int reqWidth, int reqHeight) {

		try {// First decode with inJustDecodeBounds=true to check dimensions
			BitmapFactory.Options options = new BitmapFactory.Options();
			InputStream is;
			options.inJustDecodeBounds = true;

			is = context.getContentResolver().openInputStream(imageUri);
			BitmapFactory.decodeStream(is, null, options);
			is.close();

			// rotate as necessary
			int rotatedWidth, rotatedHeight;
			int orientation = (int) rotationForImage(context, imageUri);
			if (orientation == 90 || orientation == 270) {
				rotatedWidth = options.outHeight;
				rotatedHeight = options.outWidth;
			}
			else {
				rotatedWidth = options.outWidth;
				rotatedHeight = options.outHeight;
			}

			Bitmap srcBitmap;
			is = context.getContentResolver().openInputStream(imageUri);
			if (rotatedWidth > SurespotConstants.MAX_IMAGE_DIMENSION || rotatedHeight > SurespotConstants.MAX_IMAGE_DIMENSION) {
				float widthRatio = ((float) rotatedWidth) / ((float) SurespotConstants.MAX_IMAGE_DIMENSION);
				float heightRatio = ((float) rotatedHeight) / ((float) SurespotConstants.MAX_IMAGE_DIMENSION);
				float maxRatio = Math.max(widthRatio, heightRatio);

				// Create the bitmap from file
				options = new BitmapFactory.Options();
				options.inSampleSize = (int) Math.round(maxRatio);
				SurespotLog.v(TAG, "Rotated width: " + rotatedWidth + ", height: " + rotatedHeight + ", insamplesize: "
						+ options.inSampleSize);
				srcBitmap = BitmapFactory.decodeStream(is, null, options);
			}
			else {
				srcBitmap = BitmapFactory.decodeStream(is);
			}

			SurespotLog.v(TAG, "loaded width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());
			is.close();

			if (orientation > 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation);

				srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true);
				SurespotLog.v(TAG, "post rotated width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());
			}

			return srcBitmap;
		}

		catch (Exception e) {
			SurespotLog.w(TAG, "decodeSampledBitmapFromUri", e);
		}
		return null;

		// Calculate inSampleSize
		// options.inSampleSize = 8;// calculateInSampleSize(options, reqWidth, reqHeight);
		//
		// // Decode bitmap with inSampleSize set
		// options.inJustDecodeBounds = false;
		// try {
		// return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageUri), null, options);
		// }
		// catch (FileNotFoundException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// return null;

	}

	public static Bitmap getSampledImage(byte[] data, int reqHeight) {
		BitmapFactory.Options options = new Options();
		decodeBounds(options, data);

		if (options.outHeight > reqHeight) {
			options.inSampleSize = calculateInSampleSize(options, 0, reqHeight);
		}

		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	private static void decodeBounds(Options options, byte[] data) {
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			// if (width > height) {
			inSampleSize = Math.round((float) height / (float) reqHeight);
			// }
			// else {
			// inSampleSize = Math.round((float) width / (float) reqWidth);
			// }

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).

			if (reqWidth > 0 && reqHeight > 0) {
				final float totalPixels = width * height;

				// Anything more than 2x the requested pixels we'll sample down
				// further.
				final float totalReqPixelsCap = reqWidth * reqHeight * 2;

				while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
					inSampleSize++;

				}
			}
		}
		return inSampleSize;
	}

	public static float rotationForImage(Context context, Uri uri) {
		if (uri.getScheme().equals("content")) {
			String[] projection = { Images.ImageColumns.ORIENTATION };
			Cursor c = context.getContentResolver().query(uri, projection, null, null, null);
			if (c.moveToFirst()) {
				return c.getInt(0);
			}
		}
		else if (uri.getScheme().equals("file")) {
			try {
				ExifInterface exif = new ExifInterface(uri.getPath());
				int rotation = (int) exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_NORMAL));
				return rotation;
			}
			catch (IOException e) {
				SurespotLog.e(TAG, "Error checking exif", e);
			}
		}
		return 0f;
	}

	private static float exifOrientationToDegrees(int exifOrientation) {
		if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
			return 90;
		}
		else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
			return 180;
		}
		else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
			return 270;
		}
		return 0;
	}

	public static String mapToJsonString(Map<String, Integer> map) {
		JSONObject jsonObject = new JSONObject(map);
		return jsonObject.toString();
	}

	public static JSONArray chatMessagesToJson(Collection<SurespotMessage> messages) {

		JSONArray jsonMessages = new JSONArray();
		Iterator<SurespotMessage> iterator = messages.iterator();
		while (iterator.hasNext()) {
			jsonMessages.put(iterator.next().toJSONObject());

		}
		return jsonMessages;

	}

	public static ArrayList<SurespotMessage> jsonStringToChatMessages(String jsonMessageString) {

		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		try {
			JSONArray jsonUM = new JSONArray(jsonMessageString);
			for (int i = 0; i < jsonUM.length(); i++) {
				messages.add(SurespotMessage.toSurespotMessage(jsonUM.getJSONObject(i)));
			}
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringToChatMessages", e);
		}
		return messages;

	}

}
