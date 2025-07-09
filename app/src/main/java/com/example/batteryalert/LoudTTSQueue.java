package com.example.batteryalert;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.regex.*;
import android.os.*;
import java.io.*;

public class LoudTTSQueue {
	
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean paused = false;
    private float speechRate = 1.0f;
    private float pitch = 1.0f;
    private int oldVolume = -1;
    private AudioManager audioManager;
    private Context appContext;
    private Queue<Chunk> queue = new LinkedList<Chunk>();
    
    public int maxChunkLength = 300;
	private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable shutdownRunnable;
    private static final long IDLE_TIMEOUT_MS = 600000;
	private Chunk currentChunk;
	
	private static final String TAG = "LoudTTSQueue";

	class Chunk {
		final String text;
		final int delayMs;
		int volume;
		float speed;
		float pitchVal;
		Runnable callback;
		int id;
		File outputDir;
		List<Chunk> chunks;
		public Chunk(String text, int delayMs) {
			this.text = text;
			this.delayMs = delayMs;
		}
	}
	
    public LoudTTSQueue(Context context) {
        appContext = context.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        initTTS();
    }

    private void initTTS() {
        tts = new TextToSpeech(appContext, new TextToSpeech.OnInitListener() {
				public void onInit(int status) {
					if (status == TextToSpeech.SUCCESS) {
						ready = true;
						tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
								public void onStart(String utteranceId) {
									cancelShutdownTimeout();
								}
								public void onDone(String utteranceId) {
									restoreVolume();
									if (currentChunk.callback != null) {
										// last chunk
										if (currentChunk.outputDir != null) {
											try {
												BufferedWriter writer = new BufferedWriter(new FileWriter(new File(currentChunk.outputDir, "concat.list"), true));
												for (int i = 1; i <= currentChunk.id; i++) {
													String name = "chunk_" + i + ".wav";
													File wavFile = new File(currentChunk.outputDir, name);
													writer.write("file '" + wavFile.getAbsolutePath() + "'\n");
													File delayFile = new File(currentChunk.outputDir, "delay_" + currentChunk.chunks.get(i-1).delayMs + ".wav");
													if (delayFile.exists()) {
														writer.write("file '" + delayFile.getAbsolutePath() + "'\n");
													}
												}
												writer.flush();
												writer.close();
											} catch (Throwable t) {
												Logger.log(TAG, t);
											}
										}
										currentChunk.callback.run();
									}
									if (currentChunk.outputDir == null) {
										delay();
									} else {
										speakNext();
									}
								}
								public void onError(String utteranceId) {
									Logger.log("TTS", "Lỗi khi: " + utteranceId);
									onDone(utteranceId);
								}
								private void delay() {
									int delay = (currentChunk != null) ? currentChunk.delayMs : 0;
									if (delay > 0) {
										handler.postDelayed(new Runnable() {
												public void run() {
													speakNext();
												}
											}, delay);
									} else {
										speakNext();
									}
								}
							});
						speakNext();
					}
				}
			});
    }

    public LoudTTSQueue setDefaults(float speed, float pitch) {
        this.speechRate = speed;
        this.pitch = pitch;
        if (tts != null) {
            tts.setSpeechRate(speed);
            tts.setPitch(pitch);
        }
		return this;
    }

    public synchronized void speakLoud(String text, int volume, File outputDir, Runnable callback) {
		speakLoud(text, volume, speechRate, pitch, outputDir, callback);
    }

    public synchronized void speakNow(String text, int volume, Runnable callback) {
        speakNow(text, volume, speechRate, pitch, callback);
    }

    public synchronized void speakLoud(String text, int volume, float speed, float pitchVal, File outputDir, Runnable callback) {
		//Logger.log(TAG, "speakLoud tts " + tts);
		if (tts == null) {
			initTTS();
		}
        volume = Math.max(0, Math.min(volume, 100));

        List<Chunk> chunks = splitTextToChunks(text, maxChunkLength, speed);
        int i = 0;
		for (Chunk c: chunks) {
			c.id = ++i;
			c.speed = speed;
			c.pitchVal = pitchVal;
			c.volume = volume;
			c.outputDir = outputDir;
		}
		if (chunks.size() > 0) {
			Chunk get = chunks.get(chunks.size() - 1);
			get.callback = callback;
			get.chunks = chunks;
		}
		queue.addAll(chunks);
        if (ready && !paused && !tts.isSpeaking()) {
			//Logger.log(TAG, "ready " + ready + ", paused " + paused + ", isSpeaking " + tts.isSpeaking());
            speakNext();
        }
    }

    public synchronized void speakNow(String text, int volume, float speed, float pitchVal, Runnable callback) {
        if (tts == null)
			initTTS();
        volume = Math.max(0, Math.min(volume, 100));

        List<Chunk> chunks = splitTextToChunks(text, maxChunkLength, speed);
        for (Chunk c: chunks) {
			c.speed = speed;
			c.pitchVal = pitchVal;
			c.volume = volume;
		}
		if (chunks.size() > 0) {
			chunks.get(chunks.size()-1).callback = callback;
		}
		Queue<Chunk> newQueue = new LinkedList<Chunk>();
        newQueue.addAll(chunks);
        newQueue.addAll(queue);
        queue = newQueue;
        if (paused)
			return;
        if (tts.isSpeaking()) {
            tts.stop();
        } else if (ready) {
            speakNext();
        }
    }

    private void speakNext() {
        if (!ready || tts == null || tts.isSpeaking() || paused)
			return;
        currentChunk = queue.poll();
        if (currentChunk == null) {
            scheduleShutdownTimeout();
            return;
        }
		//Logger.log(TAG, currentChunk.text);
		tts.setLanguage(Locale.getDefault());
		tts.setSpeechRate(currentChunk.speed);
		tts.setPitch(currentChunk.pitchVal);
		
        if (oldVolume == -1) {
            oldVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
        }
        if (Build.VERSION.SDK_INT >= 21) {
			Bundle params = new Bundle();
			params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
			params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (float)currentChunk.volume / 100);
			params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_id_" + currentChunk.id);
			if (currentChunk.outputDir != null) {
				try {
					if (!currentChunk.outputDir.exists()) {
						currentChunk.outputDir.mkdirs();
					}
					String name = "chunk_" + currentChunk.id + ".wav";
					File wavFile = new File(currentChunk.outputDir, name);
					tts.synthesizeToFile(currentChunk.text,
										 params,
										 wavFile,
										 wavFile.getAbsolutePath());
				}
				catch (Throwable e) {
					Logger.log(TAG, e);
				}
			} else {
				tts.speak(currentChunk.text, TextToSpeech.QUEUE_FLUSH, params, "tts_id."+currentChunk.id);
			}
		} else {
			HashMap<String, String> params = new HashMap<String, String>();
			params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
			params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf((float)currentChunk.volume / 100));
			params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_id_" + currentChunk.id);
			tts.speak(currentChunk.text, TextToSpeech.QUEUE_FLUSH, params);
		}
    }

    private void restoreVolume() {
        if (oldVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0);
            oldVolume = -1;
        }
    }

    public void pause() {
		paused = true;
        Logger.log(TAG, "tts paused");
        if (tts != null)
			tts.stop();
    }

    public void resume() {
        paused = false;
		Logger.log(TAG, "tts resumed");
        speakNext();
    }

    public void shutdown() {
        cancelShutdownTimeout();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
            queue.clear();
			paused = false;
        }
		Logger.log(TAG, "tts shutdown");
	}

    private void scheduleShutdownTimeout() {
        if (shutdownRunnable != null) {
            handler.removeCallbacks(shutdownRunnable);
        }
        shutdownRunnable = new Runnable() {
            public void run() {
                shutdown();
            }
        };
        handler.postDelayed(shutdownRunnable, IDLE_TIMEOUT_MS);
    }

    private void cancelShutdownTimeout() {
        if (shutdownRunnable != null) {
            handler.removeCallbacks(shutdownRunnable);
            shutdownRunnable = null;
        }
    }

    public static int estimatePauseDuration(String text, float speed) {
        text = text.trim();
        int base = 100;
        if (text.endsWith(".")
			|| text.endsWith("!")
			|| text.endsWith("?"))
			base = 500;
        else if (text.endsWith(",")
				 || text.endsWith(":")
				 || text.endsWith(";"))
			base = 200;
        else if (text.endsWith("\n"))
			base = 700;
        return (int)(base / speed);
    }

	public List<Chunk> splitTextToChunks(String input, int maxLength, float speed) {
        List<Chunk> chunks = new ArrayList<>();
        if (input == null || input.isEmpty()) return chunks;

        Pattern pattern = Pattern.compile("[.,;:!?\n]+|[^.,;:!?\n]+[.,;:!?\n]*");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            String group = matcher.group();
			final int groupLength = group.length();
			if (groupLength <= maxLength) {
				int pause = estimatePauseDuration(group, speed);
				chunks.add(new Chunk(group, pause));
			} else {
				int start = 0;
				while (start < groupLength) {
					int end = Math.min(groupLength, start + maxLength);
					String substring = group.substring(start, end);
					int lastIndexOf = substring.lastIndexOf(" ");
					if (lastIndexOf == -1) {
						start += substring.length();
					} else {
						substring = substring.substring(0, lastIndexOf);
						start += lastIndexOf + 1;
					}
					int pause = estimatePauseDuration(substring, speed);
					chunks.add(new Chunk(substring, pause));
				}
			}
        }
        return chunks;
    }
}
