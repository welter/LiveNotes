package com.joshschriever.livenotes.musicxml;

import com.joshschriever.livenotes.midi.ShortMessageRecipient;

import jp.kshoji.javax.sound.midi.ShortMessage;

public class MidiToXMLRenderer implements ShortMessageRecipient {

    private Callbacks callbacks;
    private MusicXmlRenderer renderer;
    private MidiParser parser;

    private boolean ready = false;
    private boolean recording = false;

    public MidiToXMLRenderer(Callbacks callbacks, int beats, int beatValue, int tempo) {
        this.callbacks = callbacks;

        DurationHandler durationHandler = new DurationHandler(beats, beatValue, tempo);
        renderer = new MusicXmlRenderer(durationHandler, beats, beatValue, tempo);

        parser = new MidiParser(durationHandler);
        parser.addParserListener(renderer);
    }

    public void setReady() {
        ready = true;
    }

    public void startRecording() {
        if (ready && !recording) {
            parser.startWithRests();
            callbacks.onXMLUpdated();
            recording = true;
        }
    }

    public void stopRecording() {
        if (recording) {
            ready = false;
            recording = false;
            parser.stop();
            renderer.removeTrailingEmptyMeasures();
            callbacks.onXMLUpdated();
        }
    }

    @Override
    public void messageReady(ShortMessage midiMessage, long timeStamp) {
        if (ready) {
            if (!recording) {
                recording = true;
                callbacks.onStartRecording();

                parser.startWithNote(midiMessage);
            } else {
                parser.parse(midiMessage);
            }
            callbacks.onXMLUpdated();
        }
    }

    public String getXML() {
        return renderer.getMusicXMLString();
    }

    public interface Callbacks {

        void onXMLUpdated();

        void onStartRecording();
    }

}
