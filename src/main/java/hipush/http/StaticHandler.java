package hipush.http;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.activation.MimetypesFileTypeMap;


public class StaticHandler {

	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_CACHE_SECONDS = 60;

	public static void sendFile(ChannelHandlerContext ctx,
			FullHttpRequest request) throws Exception {
		File file = new File(request.uri());
		if (file.isHidden() || !file.exists()) {
			sendError(ctx, NOT_FOUND);
			return;
		}

		if (!file.isFile()) {
			sendError(ctx, FORBIDDEN);
			return;
		}

		// Cache Validation
		String ifModifiedSince = request.headers().getAndConvert(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException ignore) {
			sendError(ctx, NOT_FOUND);
			return;
		}
		long fileLength = raf.length();

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		HttpHeaderUtil.setContentLength(response, fileLength);
		setContentTypeHeader(response, file);
		setDateAndCacheHeaders(response, file);
		if (HttpHeaderUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		// Write the initial line and the header.
		ctx.write(response);

		// Write the content.
		ChannelFuture sendFileFuture;
		ChannelFuture lastContentFuture;
		if (ctx.pipeline().get(SslHandler.class) == null) {
			sendFileFuture = ctx.write(new DefaultFileRegion(raf.getChannel(),
					0, fileLength), ctx.newProgressivePromise());
			// Write the end marker.
			lastContentFuture = ctx
					.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		} else {
			sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(
					new ChunkedFile(raf, 0, fileLength, 8192)), ctx
					.newProgressivePromise());
			// HttpChunkedInput will write the end marker (LastHttpContent) for
			// us.
			lastContentFuture = sendFileFuture;
		}

		sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future,
					long progress, long total) {
				if (total < 0) { // total unknown
					System.err.println(future.channel()
							+ " Transfer progress: " + progress);
				} else {
					System.err.println(future.channel()
							+ " Transfer progress: " + progress + " / " + total);
				}
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture future) {
				System.err.println(future.channel() + " Transfer complete.");
			}
		});

		// Decide whether to close the connection or not.
		if (!HttpHeaderUtil.isKeepAlive(request)) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private static void sendError(ChannelHandlerContext ctx,
			HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
				status, Unpooled.copiedBuffer("Failure: " + status + "\r\n",
						CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	/**
	 * When file timestamp is the same as what the browser is sending up, send a
	 * "304 Not Modified"
	 *
	 * @param ctx
	 *            Context
	 */
	private static void sendNotModified(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
				NOT_MODIFIED);
		setDateHeader(response);

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	/**
	 * Sets the Date header for the HTTP response
	 *
	 * @param response
	 *            HTTP response
	 */
	private static void setDateHeader(FullHttpResponse response) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,
				Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
	}

	/**
	 * Sets the Date and Cache headers for the HTTP Response
	 *
	 * @param response
	 *            HTTP response
	 * @param fileToCache
	 *            file to extract content type
	 */
	private static void setDateAndCacheHeaders(HttpResponse response,
			File fileToCache) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,
				Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		// Date header
		Calendar time = new GregorianCalendar();
		response.headers().set("date", dateFormatter.format(time.getTime()));

		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CACHE_CONTROL,
				"private, max-age=" + HTTP_CACHE_SECONDS);
		response.headers().set("last-modified",
				dateFormatter.format(new Date(fileToCache.lastModified())));
	}

	/**
	 * Sets the content type header for the HTTP Response
	 *
	 * @param response
	 *            HTTP response
	 * @param file
	 *            file to extract content type
	 */
	private static void setContentTypeHeader(HttpResponse response, File file) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		response.headers().set(HttpHeaderNames.CONTENT_TYPE,
				mimeTypesMap.getContentType(file.getPath()));
	}
}