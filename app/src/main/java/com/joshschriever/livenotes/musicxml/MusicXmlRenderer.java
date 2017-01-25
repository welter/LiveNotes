package com.joshschriever.livenotes.musicxml;

import org.jfugue.ChannelPressure;
import org.jfugue.Controller;
import org.jfugue.Instrument;
import org.jfugue.KeySignature;
import org.jfugue.Layer;
import org.jfugue.Measure;
import org.jfugue.Note;
import org.jfugue.ParserListener;
import org.jfugue.PitchBend;
import org.jfugue.PolyphonicPressure;
import org.jfugue.Tempo;
import org.jfugue.Time;
import org.jfugue.Voice;

import java8.util.Spliterator;
import java8.util.Spliterators.AbstractSpliterator;
import java8.util.function.Consumer;
import java8.util.function.Predicate;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;
import nu.xom.Attribute;
import nu.xom.DocType;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

// Forked from JFugue
public class MusicXmlRenderer implements ParserListener {

    private Document document;
    private Element root = new Element("score-partwise");
    private Element elCurMeasure;
    private Element elPartList;
    private Element elCurScorePart;
    private Element elCurPart;

    public MusicXmlRenderer() {
        Element elID = new Element("identification");
        Element elCreator = new Element("creator");
        elCreator.addAttribute(new Attribute("type", "software"));
        elCreator.appendChild("JFugue MusicXMLRenderer");
        elID.appendChild(elCreator);
        root.appendChild(elID);
        elPartList = new Element("part-list");
        root.appendChild(elPartList);
        document = new Document(root);
        document.insertChild(new DocType("score-partwise",
                                         "-//Recordare//DTD MusicXML 1.1 Partwise//EN",
                                         "http://www.musicxml.org/dtds/partwise.dtd"),
                             0);
    }

    public String getMusicXMLString() {
        return getInternalMusicXMLString().replaceAll("<duration>0</duration>",
                                                      "<duration>1</duration>");
    }

    protected String getInternalMusicXMLString() {
        return getMusicXMLDoc().toXML();
    }

    public Document getMusicXMLDoc() {
        finishCurrentVoice();
        Elements elDocParts = root.getChildElements("part");

        for (int xomDoc = 0; xomDoc < elDocParts.size(); ++xomDoc) {
            Element docType = elDocParts.get(xomDoc);
            Elements elPartMeasures = docType.getChildElements("measure");

            for (int xM = 0; xM < elPartMeasures.size(); ++xM) {
                if (elPartMeasures.get(xM).getChildCount() < 1) {
                    docType.removeChild(xM);
                }
            }
        }

        return document;
    }

    public void doFirstMeasure(boolean bAddDefaults) {
        if (elCurPart == null) {
            newVoice(new Voice((byte) 0));
        }

        if (elCurMeasure == null) {
            elCurMeasure = new Element("measure");
            elCurMeasure.addAttribute(new Attribute("number", Integer.toString(1)));
            Element elAttributes = new Element("attributes");
            Element elClef;
            Element elSign;
            Element elLine;
            if (bAddDefaults) {
                elClef = new Element("divisions");
                elClef.appendChild(Integer.toString(4));
                elAttributes.appendChild(elClef);

                Element elKey = new Element("key");
                Element elFifths = new Element("fifths");
                elFifths.appendChild("0");
                elKey.appendChild(elFifths);
                Element elMode = new Element("mode");
                elMode.appendChild("major");
                elKey.appendChild(elMode);
                elAttributes.appendChild(elKey);

                elSign = new Element("time");
                elLine = new Element("beats");
                elLine.appendChild(Integer.toString(4));
                elSign.appendChild(elLine);
                Element elBeatType = new Element("beat-type");
                elBeatType.appendChild(Integer.toString(4));
                elSign.appendChild(elBeatType);
                elAttributes.appendChild(elSign);

                elClef = new Element("clef");
                elSign = new Element("sign");
                elSign.appendChild("G");
                elLine = new Element("line");
                elLine.appendChild("2");
                elClef.appendChild(elSign);
                elClef.appendChild(elLine);
                elAttributes.appendChild(elClef);
            }

            if (elAttributes.getChildCount() > 0) {
                elCurMeasure.appendChild(elAttributes);
            }

            if (bAddDefaults) {
                doTempo(new Tempo(120));
            }
        }
    }

    public void voiceEvent(Voice voice) {
        String sReqVoice = voice.getMusicString();
        String sCurPartID =
                elCurPart == null ? null : elCurPart.getAttribute("id").getValue();
        if (sCurPartID == null || sReqVoice.compareTo(sCurPartID) != 0) {
            boolean bNewVoiceExists = false;
            Elements elParts = root.getChildElements("part");
            Element elExistingNewPart = null;

            for (int x = 0; x < elParts.size(); ++x) {
                Element elP = elParts.get(x);
                String sPID = elP.getAttribute("id").getValue();
                if (sPID.compareTo(sReqVoice) == 0) {
                    bNewVoiceExists = true;
                    elExistingNewPart = elP;
                }
            }

            finishCurrentVoice();
            if (bNewVoiceExists) {
                elCurPart = elExistingNewPart;
            } else {
                newVoice(voice);
            }

            newMeasure();
        }
    }

    private void finishCurrentVoice() {
        String sCurPartID = elCurPart == null ? null : elCurPart.getAttribute("id").getValue();
        boolean bCurVoiceExists = false;
        Elements elParts = root.getChildElements("part");
        Element elExistingCurPart = null;

        for (int x = 0; x < elParts.size(); ++x) {
            Element elP = elParts.get(x);
            String sPID = elP.getAttribute("id").getValue();
            if (sPID.compareTo(sCurPartID) == 0) {
                bCurVoiceExists = true;
                elExistingCurPart = elP;
            }
        }

        if (elCurPart != null) {
            finishCurrentMeasure();
            if (bCurVoiceExists) {
                root.replaceChild(elExistingCurPart, elCurPart);
            } else {
                root.appendChild(elCurPart);
            }
        }

    }

    private void newVoice(Voice voice) {
        elCurScorePart = new Element("score-part");
        Attribute atPart = new Attribute("id", voice.getMusicString());
        elCurScorePart.addAttribute(atPart);
        elCurScorePart.appendChild(new Element("part-name"));
        Element elPL = root.getFirstChildElement("part-list");
        elPL.appendChild(elCurScorePart);
        elCurPart = new Element("part");
        Attribute atPart2 = new Attribute(atPart);
        elCurPart.addAttribute(atPart2);
        elCurMeasure = null;
        doFirstMeasure(true);
    }

    public void instrumentEvent(Instrument instrument) {
        Element elInstrName = new Element("instrument-name");
        elInstrName.appendChild(instrument.getInstrumentName());
        Element elInstrument = new Element("score-instrument");
        elInstrument.addAttribute(new Attribute("id", Byte.toString(instrument.getInstrument())));
        elInstrument.appendChild(elInstrName);
    }

    public void tempoEvent(Tempo tempo) {
        doTempo(tempo);
    }

    private void doTempo(Tempo tempo) {
        Element elDirection = new Element("direction");
        elDirection.addAttribute(new Attribute("placement", "above"));
        Element elDirectionType = new Element("direction-type");
        Element elMetronome = new Element("metronome");
        Element elBeatUnit = new Element("beat-unit");
        elBeatUnit.appendChild("quarter");
        Element elPerMinute = new Element("per-minute");
        Integer iBPM = Float.valueOf(PPMtoBPM(tempo.getTempo())).intValue();
        elPerMinute.appendChild(iBPM.toString());
        elMetronome.appendChild(elBeatUnit);
        elMetronome.appendChild(elPerMinute);
        elDirectionType.appendChild(elMetronome);
        elDirection.appendChild(elDirectionType);
        if (elCurMeasure == null) {
            doFirstMeasure(true);
        }

        elCurMeasure.appendChild(elDirection);
    }

    public void layerEvent(Layer layer) {
    }

    public void timeEvent(Time time) {
    }

    public void keySignatureEvent(KeySignature keySig) {
        doKeySig(keySig);
    }

    private void doKeySig(KeySignature keySig) {
        Element elKey = new Element("key");
        Element elFifths = new Element("fifths");
        elFifths.appendChild(Byte.toString(keySig.getKeySig()));
        elKey.appendChild(elFifths);
        Element elMode = new Element("mode");
        elMode.appendChild(keySig.getScale() == 1 ? "minor" : "major");
        elKey.appendChild(elMode);
        if (elCurMeasure == null) {
            doFirstMeasure(true);
        }

        Element elAttributes = elCurMeasure.getFirstChildElement("attributes");
        boolean bNewAttributes = elAttributes == null;
        if (bNewAttributes) {
            elAttributes = new Element("attributes");
        }

        elAttributes.appendChild(elKey);
        if (bNewAttributes) {
            elCurMeasure.appendChild(elAttributes);
        }

    }

    public void measureEvent(Measure measure) {
        if (elCurMeasure == null) {
            doFirstMeasure(false);
        } else {
            finishCurrentMeasure();
            newMeasure();
        }

    }

    private void finishCurrentMeasure() {
        if (elCurMeasure.getParent() == null) {
            elCurPart.appendChild(elCurMeasure);
        } else {
            int sCurMNum = Integer.parseInt(elCurMeasure.getAttributeValue("number"));
            Elements elMeasures = elCurPart.getChildElements("measure");

            for (int x = 0; x < elMeasures.size(); ++x) {
                Element elM = elMeasures.get(x);
                int sMNum = Integer.parseInt(elM.getAttributeValue("number"));
                if (sMNum == sCurMNum) {
                    elCurPart.replaceChild(elM, elCurMeasure);
                }
            }
        }

    }

    private void newMeasure() {
        int nextNumber = 1;
        boolean bNewMeasure = true;
        Elements elMeasures = elCurPart.getChildElements("measure");
        Element elLastMeasure;
        if (elMeasures.size() > 0) {
            elLastMeasure = elMeasures.get(elMeasures.size() - 1);
            Attribute elNumber = elLastMeasure.getAttribute("number");
            if (elLastMeasure.getChildElements("note").size() < 1) {
                bNewMeasure = false;
            } else {
                nextNumber = Integer.parseInt(elNumber.getValue()) + 1;
            }
        } else {
            bNewMeasure = elCurMeasure.getChildElements("note").size() > 0;
        }

        if (bNewMeasure) {
            elCurMeasure = new Element("measure");
            elCurMeasure.addAttribute(new Attribute("number", Integer.toString(nextNumber)));
        }

    }

    public void controllerEvent(Controller controller) {
    }

    public void channelPressureEvent(ChannelPressure channelPressure) {
    }

    public void polyphonicPressureEvent(PolyphonicPressure polyphonicPressure) {
    }

    public void pitchBendEvent(PitchBend pitchBend) {
    }

    public void noteEvent(Note note) {
        doNote(note, false);
    }

    private void doNote(Note note, boolean bChord) {
        Element elNote = new Element("note");
        if (bChord) {
            elNote.appendChild(new Element("chord"));
        }

        int iAlter = alterForNoteValue(note.getValue());
        if (note.isRest()) {
            elNote.appendChild(new Element("rest"));
        } else {
            Element elPitch = new Element("pitch");
            Element elStep = new Element("step");
            elStep.appendChild(stepForNoteValue(note.getValue()));
            elPitch.appendChild(elStep);

            if (iAlter != 0) {
                Element elAlter = new Element("alter");
                elAlter.appendChild(Integer.toString(iAlter));
                elPitch.appendChild(elAlter);
            }

            Element elOctave = new Element("octave");
            elOctave.appendChild(octaveForNoteValue(note.getValue()));
            elPitch.appendChild(elOctave);
            elNote.appendChild(elPitch);
        }

        Element elDuration = new Element("duration");
        double decimalDuration = note.getDecimalDuration();
        int iXMLDuration = (int) (decimalDuration * 1024.0D * 4.0D / 256.0D);
        elDuration.appendChild(Integer.toString(iXMLDuration));
        elNote.appendChild(elDuration);

        Element elTie;
        Attribute atTieType;
        boolean bTied = false;
        if (note.isStartOfTie()) {
            elTie = new Element("tie");
            atTieType = new Attribute("type", "start");
            elTie.addAttribute(atTieType);
            elNote.appendChild(elTie);
            bTied = true;
        } else if (note.isEndOfTie()) {
            elTie = new Element("tie");
            atTieType = new Attribute("type", "stop");
            elTie.addAttribute(atTieType);
            elNote.appendChild(elTie);
            bTied = true;
        }

        String sType;
        boolean bDotted = false;
        /*if (decimalDuration <= 0.0078125D) {
            sType = "128th";
        } else if (decimalDuration <= 0.01171875D) {
            sType = "128th";
            bDotted = true;
        } else if (decimalDuration <= 0.015625D) {
            sType = "64th";
        } else if (decimalDuration <= 0.0234375D) {
            sType = "64th";
            bDotted = true;
        } else if (decimalDuration <= 0.03125D) {
            sType = "32nd";
        } else if (decimalDuration <= 0.046875D) {
            sType = "32nd";
            bDotted = true;
        } else*/
        if (decimalDuration <= 0.0625D) {
            sType = "16th";
        } else if (decimalDuration <= 0.09375D) {
            sType = "16th";
            bDotted = true;
        } else if (decimalDuration <= 0.125D) {
            sType = "eighth";
        } else if (decimalDuration <= 0.1875D) {
            sType = "eighth";
            bDotted = true;
        } else if (decimalDuration <= 0.25D) {
            sType = "quarter";
        } else if (decimalDuration <= 0.375D) {
            sType = "quarter";
            bDotted = true;
        } else if (decimalDuration <= 0.5D) {
            sType = "half";
        } else if (decimalDuration <= 0.75D) {
            sType = "half";
            bDotted = true;
        } else {
            sType = "whole";
        }

        Element elType = new Element("type");
        elType.appendChild(sType);
        elNote.appendChild(elType);
        if (bDotted) {
            elNote.appendChild(new Element("dot"));
        }
        if (iAlter != 0) {
            Element elAccidental = new Element("accidental");
            elAccidental.appendChild(iAlter == 1 ? "sharp" : "flat");
            elNote.appendChild(elAccidental);
        }

        if (bTied) {
            Element elNotations = new Element("notations");
            Element elTied;
            Attribute atType;
            if (note.isStartOfTie()) {
                elTied = new Element("tied");
                atType = new Attribute("type", "start");
                elTied.addAttribute(atType);
                elNotations.appendChild(elTied);
            } else if (note.isEndOfTie()) {
                elTied = new Element("tied");
                atType = new Attribute("type", "stop");
                elTied.addAttribute(atType);
                elNotations.appendChild(elTied);
            }

            elNote.appendChild(elNotations);
        }

        if (iXMLDuration == 0) {
            elCurMeasure.appendChild(elNote);
        } else {
            stream(root.getChildElements("part"))
                    .flatMap(part -> stream(part.getChildElements("measure"))
                            .flatMap(measure -> stream(measure.getChildElements("note"))))
                    .filter(noteMatches(note.getValue(), 0)).findFirst()
                    .ifPresent(elOldNote -> elCurMeasure.replaceChild(elOldNote, elNote));
        }
    }

    private static String stepForNoteValue(int value) {
        return Note.NOTES[value % 12].substring(0, 1);
    }

    private static int alterForNoteValue(int value) {
        String pitch = Note.NOTES[value % 12];
        return pitch.length() > 1 ? pitch.contains("#") ? 1 : -1 : 0;
    }

    private static String octaveForNoteValue(int value) {
        return Integer.toString(value / 12);
    }

    public void sequentialNoteEvent(Note note) {
    }

    public void parallelNoteEvent(Note note) {
        doNote(note, true);
    }

    public static float PPMtoBPM(int ppm) {
        return 14400.0F / (float) ppm;
    }

    private static Predicate<Element> noteMatches(int value, int duration) {
        return elNote -> pitchMatches(elNote.getFirstChildElement("pitch"), value)
                && elNote.getFirstChildElement("duration")
                         .getValue().equals(Integer.toString(duration));
    }

    private static boolean pitchMatches(Element elPitch, int value) {
        return elPitch.getFirstChildElement("step").getValue()
                      .equals(stepForNoteValue(value))
                && elPitch.getFirstChildElement("octave").getValue()
                          .equals(octaveForNoteValue(value))
                && alterMatches(elPitch.getFirstChildElement("alter"), value);
    }

    private static boolean alterMatches(Element elAlter, int value) {
        int alter = alterForNoteValue(value);
        return alter == 0 ? elAlter == null
                          : elAlter != null && elAlter.getValue().equals(Integer.toString(alter));
    }

    private static Stream<Element> stream(Elements elements) {
        return StreamSupport.stream(new ElementsSpliterator(elements), false);
    }

    private static class ElementsSpliterator extends AbstractSpliterator<Element> {

        private Elements elements;
        private int current = 0;

        private ElementsSpliterator(Elements elements) {
            super(elements.size(), Spliterator.ORDERED | Spliterator.SIZED | Spliterator.IMMUTABLE);
            this.elements = elements;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Element> action) {
            if (current < elements.size()) {
                action.accept(elements.get(current));
                current++;
                return true;
            } else {
                return false;
            }
        }
    }

}