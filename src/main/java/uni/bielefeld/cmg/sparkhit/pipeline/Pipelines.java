package uni.bielefeld.cmg.sparkhit.pipeline;


import uni.bielefeld.cmg.sparkhit.io.FastqUnitBuffer;
import uni.bielefeld.cmg.sparkhit.io.TextFileBufferInput;
import uni.bielefeld.cmg.sparkhit.io.TextFileBufferOutput;
import uni.bielefeld.cmg.sparkhit.matrix.ScoreMatrix;
import uni.bielefeld.cmg.sparkhit.reference.RefSerializer;
import uni.bielefeld.cmg.sparkhit.reference.RefStructBuilder;
import uni.bielefeld.cmg.sparkhit.reference.RefStructSerializer;
import uni.bielefeld.cmg.sparkhit.util.DefaultParam;
import uni.bielefeld.cmg.sparkhit.util.InfoDumper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Created by rhinempi on 27/01/16.
 *
 *      SparkHit
 *
 * Copyright (c) 2015-2015
 * Liren Huang      <huanglr at cebitec.uni-bielefeld.de>
 *
 * SparkHit is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; Without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more detail.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses>.
 */


public class Pipelines implements Pipeline {

    private String threadName;

    private TextFileBufferInput inputFileBuffer;
    private BufferedReader inputBufferedReader;
    private FastqUnitBuffer inputFastqUnitBuffer;

    private TextFileBufferOutput outputFileBuffer;
    private BufferedWriter outputBufferedWriter;

    private DefaultParam param;

    private InfoDumper info = new InfoDumper();

    private OnePipe[] processors;

    private long time;

    private RefStructBuilder ref;

    private ScoreMatrix matrix = new ScoreMatrix();

    private void clockStart(){
        time = System.currentTimeMillis();
    }

    private long clockCut(){
        long tmp = time;
        time = System.currentTimeMillis();
        return time - tmp;
    }

    /**
     *
     */
    public Pipelines () {
    }

    public void loadReference(){
        RefStructSerializer refSer = new RefStructSerializer();
        refSer.setParameter(param);

        clockStart();
        refSer.kryoDeserialization();
        long T = clockCut();
        info.readParagraphedMessages("loaded reference genome index.\ntook " + T + " ms.");
        info.screenDump();
        ref = refSer.getStruct();
    }

    public void buildReference(){

        info.readMessage("start building reference index.");
        info.screenDump();
        RefStructBuilder ref = new RefStructBuilder();

        info.readMessage("parsing parameters ...");
        info.screenDump();
        ref.setParameter(param);
        info.readParagraphedMessages("kmer size : " + param.kmerSize + "\nout put index file to : \n\t" + param.inputFaPath + ".xxx");
        info.screenDump();

        info.readMessage("start loading reference sequence.");
        info.screenDump();
        clockStart();
        ref.loadRef(param.inputFaPath);
        long T = clockCut();
        info.readParagraphedMessages("loaded " + ref.totalLength + " bases, " + ref.totalNum + " contigs.\ntook " + T + " ms.");
        info.screenDump();

        info.readMessage("start building reference index.");
        info.screenDump();
        clockStart();
        ref.buildIndex();
        T = clockCut();
        info.readMessage("took " + T + " ms.");
        info.screenDump();

        RefStructSerializer refSer = new RefStructSerializer();
        refSer.setParameter(param);
        refSer.setStruct(ref);
        info.readParagraphedMessages("start writing index to : \n\t" + param.inputFaPath + ".xxx");
        info.screenDump();
        clockStart();
        refSer.kryoSerialization();
        T = clockCut();
        info.readMessage("took " + T + " ms");
        info.screenDump();

        info.readMessage("finish building reference index.");
        info.screenDump();
    }

    /**
     *
     * @param cores
     */
    public void parallelization(int cores) {
        inputFileBuffer = new TextFileBufferInput();
        inputFileBuffer.setInput(param.inputFqPath);
        inputBufferedReader = inputFileBuffer.getBufferReader();

        inputFastqUnitBuffer = new FastqUnitBuffer(inputBufferedReader);

        outputFileBuffer = new TextFileBufferOutput();
        outputFileBuffer.setOutput(param.outputPath, false);
        outputBufferedWriter = outputFileBuffer.getOutputBufferWriter();

        processors = new OnePipe[cores];

        info.readMessage("Main pipeline is now alive, start parallelizing.");
        info.screenDump();

        for (int i = 0; i < cores; i++) {
            threadName = "processor P" + i;
            newThread(i);
        }

        for (int i = 0; i < cores; i++) {
            processors[i].join();
        }

        /**
         *  looping each entry of a tar file
         */
        if (inputFileBuffer.fileFormatIntMark == 3 || inputFileBuffer.fileFormatIntMark == 4) {
            while (inputFileBuffer.setNextTarArchiveEntry()) {
                inputBufferedReader = inputFileBuffer.getBufferReader();  // re-initial bufferReader to next tar entry
                inputFastqUnitBuffer = new FastqUnitBuffer(inputBufferedReader);  // re-initial FastqUnitBuffer

                for (int i = 0; i < cores; i++) {
                    threadName = "processor P" + i;
                    newThread(i);
                }

                for (int i = 0; i < cores; i++) {
                    processors[i].join();
                }
            }
        }

        try {
            outputBufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        info.readMessage("Main pipeline finished.");
        info.screenDump();
    }

    public void newThread(int pipeIndex){
        OnePipe pipe = new OnePipe(threadName);
        pipe.setParameter(param);
        pipe.setStruct(ref);
        pipe.setMatrix(matrix);
        pipe.setInputFastqUnitBuffer(inputFastqUnitBuffer);
        pipe.setOutput(outputBufferedWriter);
        pipe.start();
        processors[pipeIndex] = pipe;
    }

    public void setFastqUnitBuffer(FastqUnitBuffer inputFastqUnitBuffer){
        this.inputFastqUnitBuffer = inputFastqUnitBuffer;
    }

    public void setParameter(DefaultParam param){
        this.param = param;
    }

    public void setInput(BufferedReader inputBufferedReader){

    }

    public void setOutput(BufferedWriter outputBufferedWriter){
        this.outputBufferedWriter = outputBufferedWriter;
    }
}
