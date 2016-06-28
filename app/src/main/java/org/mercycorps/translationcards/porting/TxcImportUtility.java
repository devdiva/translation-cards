package org.mercycorps.translationcards.porting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mercycorps.translationcards.model.DbManager;
import org.mercycorps.translationcards.repository.DeckRepository;
import org.mercycorps.translationcards.repository.DictionaryRepository;
import org.mercycorps.translationcards.service.LanguageService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class to handle constructing and reading .txc files.
 *
 * @author nick.c.worden@gmail.com (Nick Worden)
 */
public class TxcImportUtility {

    private static final String INDEX_FILENAME = "card_deck.csv";
    private static final String ALT_INDEX_FILENAME = "card_deck.txt";
    private static final String SPEC_FILENAME = "card_deck.json";
    private static final int BUFFER_SIZE = 2048;
    private static final String DEFAULT_SOURCE_LANGUAGE = "eng";
    private LanguageService languageService;

    public TxcImportUtility(LanguageService languageService) {
        this.languageService = languageService;
    }

    public ImportSpec prepareImport(Context context, Uri source) throws ImportException {
        String hash = getFileHash(context, source);
        ZipInputStream zip = getZip(context, source);
        String filename = source.getLastPathSegment();
        File targetDir = getImportTargetDirectory(context, filename);
        String indexFilename = readFiles(zip, targetDir);
        return getIndex(targetDir, indexFilename, filename, hash);
    }

    public void executeImport(Context context, ImportSpec importSpec) throws ImportException {
        loadData(context, importSpec, false);
    }

    public void abortImport(ImportSpec importSpec) {
        importSpec.dir.delete();
    }

    public boolean isExistingDeck(Context context, ImportSpec importSpec) {
        DbManager dbManager = new DbManager(context, languageService);
        DictionaryRepository dictionaryRepository = new DictionaryRepository(dbManager);
        return new DeckRepository(dictionaryRepository, dbManager.getDbh()).hasDeckWithHash(importSpec.hash);
    }

    public long otherVersionExists(Context context, ImportSpec importSpec) {
        DbManager dbManager = new DbManager(context, languageService);
        DictionaryRepository dictionaryRepository = new DictionaryRepository(dbManager);
        return new DeckRepository(dictionaryRepository, dbManager.getDbh()).hasDeckWithExternalId(importSpec.externalId);
    }

    private String getFileHash(Context context, Uri source) throws ImportException {
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(source);
        } catch (FileNotFoundException e) {
            throw new ImportException(ImportException.ImportProblem.FILE_NOT_FOUND, e);
        }
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ImportException(null, e);
        }
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new ImportException(ImportException.ImportProblem.READ_ERROR, e);
        }
        return (new BigInteger(md.digest())).toString(16);
    }

    private ZipInputStream getZip(Context context, Uri source) throws ImportException {
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(source);
        } catch (FileNotFoundException e) {
            throw new ImportException(ImportException.ImportProblem.FILE_NOT_FOUND, e);
        }
        return new ZipInputStream(inputStream);
    }

    private File getImportTargetDirectory(Context context, String filename) {
        File recordingsDir = new File(context.getFilesDir(), "recordings");
        File targetDir = new File(recordingsDir, String.format("%s-%d",
                filename, (new Random()).nextInt()));
        targetDir.mkdirs();
        return targetDir;
    }

    private String readFiles(ZipInputStream zip, File targetDir) throws ImportException {
        String indexFilename = null;
        FileOutputStream outputStream = null;
        Exception readError = null;
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                String name = zipEntry.getName();
                if (INDEX_FILENAME.equals(name)
                        || ALT_INDEX_FILENAME.equals(name)
                        || SPEC_FILENAME.equals(name)) {
                    indexFilename = name;
                }
                outputStream = new FileOutputStream(new File(targetDir, name));
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            readError = e;
        } finally {
            try {
                zip.close();
            } catch (IOException e) {
                readError = e;
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    readError = e;
                }
            }
        }
        if (readError != null) {
            throw new ImportException(ImportException.ImportProblem.READ_ERROR, readError);
        }
        if (indexFilename == null) {
            targetDir.delete();
            throw new ImportException(ImportException.ImportProblem.NO_INDEX_FILE, null);
        }
        return indexFilename;
    }

    private ImportSpec getIndex(File dir, String indexFilename, String defaultLabel, String hash)
            throws ImportException {
        if (SPEC_FILENAME.equals(indexFilename)) {
            return getIndexFromSpec(dir, hash);
        } else {
            return getIndexFromPsv(dir, indexFilename, defaultLabel, hash);
        }
    }

    private ImportSpec getIndexFromSpec(File dir, String hash) throws ImportException {
        JSONObject json;
        try {
            InputStream is = new FileInputStream(new File(dir, SPEC_FILENAME));
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            json = new JSONObject(new String(buffer, "UTF-8"));
        } catch (FileNotFoundException e) {
            throw new ImportException(ImportException.ImportProblem.NO_INDEX_FILE, e);
        } catch (IOException e) {
            throw new ImportException(ImportException.ImportProblem.INVALID_INDEX_FILE, e);
        } catch (JSONException e) {
            throw new ImportException(ImportException.ImportProblem.INVALID_INDEX_FILE, e);
        }
        return buildImportSpec(dir, hash, json);
    }

    @NonNull
    public ImportSpec buildImportSpec(File dir, String hash, JSONObject json) throws ImportException {
        ImportSpec spec;
        try {
            String deckLabel = json.getString(JsonKeys.DECK_LABEL);
            String publisher = json.optString(JsonKeys.PUBLISHER);
            String externalId = json.optString(JsonKeys.EXTERNAL_ID);
            long timestamp = json.optLong(JsonKeys.TIMESTAMP, -1);
            String srcLanguage = json.optString(JsonKeys.SOURCE_LANGUAGE, DEFAULT_SOURCE_LANGUAGE);
            boolean locked = json.optBoolean(JsonKeys.LOCKED, false);
            spec = new ImportSpec(deckLabel, publisher, externalId, timestamp, locked, srcLanguage,
                    hash, dir);
            JSONArray dictionaries = json.optJSONArray(JsonKeys.DICTIONARIES);
            if (dictionaries == null) {
                return spec;
            }
            for (int i = 0; i < dictionaries.length(); i++) {
                JSONObject dictionary = dictionaries.getJSONObject(i);
                String destIsoCode = dictionary.getString(JsonKeys.DICTIONARY_DEST_ISO_CODE);
                String language = languageService.getLanguageDisplayName(destIsoCode);
                ImportSpecDictionary dictionarySpec = new ImportSpecDictionary(destIsoCode, language);
                spec.dictionaries.add(dictionarySpec);
                JSONArray cards = dictionary.optJSONArray(JsonKeys.CARDS);
                if (cards == null) {
                    continue;
                }
                for (int j = 0; j < cards.length(); j++) {
                    JSONObject card = cards.getJSONObject(j);
                    String cardLabel = card.getString(JsonKeys.CARD_LABEL);
                    String cardFilename = card.getString(JsonKeys.CARD_DEST_AUDIO);
                    String cardTranslatedText = card.getString(JsonKeys.CARD_DEST_TEXT);
                    dictionarySpec.cards.add(new ImportSpecCard(
                            cardLabel, cardFilename, cardTranslatedText));
                }
            }
        } catch (JSONException e) {
            throw new ImportException(ImportException.ImportProblem.INVALID_INDEX_FILE, e);
        }
        return spec;
    }

    private ImportSpec getIndexFromPsv(File dir, String indexFilename, String defaultLabel,
                                       String hash) throws ImportException {
        String label = defaultLabel;
        String publisher = null;
        String externalId = null;
        long timestamp = -1;
        ImportSpec spec = new ImportSpec(label, publisher, externalId, timestamp, false,
                DEFAULT_SOURCE_LANGUAGE, hash, dir);
        Map<String, ImportSpecDictionary> dictionaryLookup = new HashMap<>();
        Scanner s;
        try {
            s = new Scanner(new File(dir, indexFilename));
        } catch (FileNotFoundException e) {
            throw new ImportException(ImportException.ImportProblem.NO_INDEX_FILE, e);
        }
        boolean isFirstLine = true;
        while (s.hasNextLine()) {
            String line = s.nextLine().trim();
            if (isFirstLine) {
                isFirstLine = false;
                // It was the first line; see if it's meta information.
                if (line.startsWith("META:")) {
                    String[] metaLine = line.substring(5).split("\\|");
                    if (metaLine.length == 4) {
                        label = metaLine[0];
                        publisher = metaLine[1];
                        externalId = metaLine[2];
                        timestamp = Long.valueOf(metaLine[3]);
                        spec = new ImportSpec(label, publisher, externalId, timestamp, false,
                                DEFAULT_SOURCE_LANGUAGE, hash, dir);
                        continue;
                    }
                }
            }
            String[] split = line.trim().split("\\|");
            if (split.length < 3) {
                s.close();
                throw new  ImportException(ImportException.ImportProblem.INVALID_INDEX_FILE, null);
            }
            String isoCode = split[2];
            ImportSpecDictionary dictionary;
            if (dictionaryLookup.containsKey(isoCode)) {
                dictionary = dictionaryLookup.get(isoCode);
            } else {
                String language = languageService.getLanguageDisplayName(isoCode);
                dictionary = new ImportSpecDictionary(isoCode, language);
                dictionaryLookup.put(isoCode, dictionary);
                spec.dictionaries.add(dictionary);
            }
            dictionary.cards.add(
                    new ImportSpecCard(split[0], split[1], split.length > 3 ? split[3] : null));
        }
        s.close();
        return spec;
    }

    public void loadData(Context context, ImportSpec importSpec, boolean isAsset) {
        DbManager dbManager = new DbManager(context, languageService);
        DictionaryRepository dictionaryRepository = new DictionaryRepository(dbManager);
        long deckId = new DeckRepository(dictionaryRepository, dbManager.getDbh()).addDeck(importSpec.label, importSpec.publisher, importSpec.timestamp,
                importSpec.externalId, importSpec.hash, importSpec.locked, importSpec.srcLanguage);
        for (int i = 0; i < importSpec.dictionaries.size(); i++) {
            ImportSpecDictionary dictionary = importSpec.dictionaries.get(i);
            long dictionaryId = dbManager.addDictionary(dictionary.isoCode, dictionary.language, i, deckId);
            for (int j = dictionary.cards.size() - 1; j >= 0; j--) {
                ImportSpecCard card = dictionary.cards.get(j);
                File cardFile = new File(importSpec.dir, card.filename);
                dbManager.addTranslation(
                        dictionaryId, card.label, false, cardFile.getAbsolutePath(), dictionary.cards.size() - j,
                        card.translatedText);
            }
        }
    }

    public void loadAssetData(SQLiteDatabase writableDatabase, Context context, ImportSpec importSpec) {
        DbManager dbManager = new DbManager(context, languageService);
        DictionaryRepository dictionaryRepository = new DictionaryRepository(dbManager);
        long deckId = new DeckRepository(dictionaryRepository, dbManager.getDbh()).addDeck(writableDatabase, importSpec.label, importSpec.publisher, importSpec.timestamp,
                importSpec.externalId, importSpec.hash, importSpec.locked, importSpec.srcLanguage);
        for (int i = 0; i < importSpec.dictionaries.size(); i++) {
            ImportSpecDictionary dictionary = importSpec.dictionaries.get(i);
            long dictionaryId = dbManager.addDictionary(writableDatabase, dictionary.isoCode, dictionary.language, i, deckId);
            for (int j = dictionary.cards.size() - 1; j >= 0; j--) {
                ImportSpecCard card = dictionary.cards.get(j);
                dbManager.addTranslation(writableDatabase, dictionaryId, card.label, true, card.filename, dictionary.cards.size() - j, card.translatedText);
            }
        }
    }

    public class ImportSpec {

        public final String label;
        public final String publisher;
        public final String externalId;
        public final long timestamp;
        public final boolean locked;
        public final String srcLanguage;
        public final String hash;
        public final File dir;
        public final List<ImportSpecDictionary> dictionaries;

        public ImportSpec(String label, String publisher, String externalId, long timestamp,
                          boolean locked, String srcLanguage, String hash, File dir) {
            this.label = label;
            this.publisher = publisher;
            this.externalId = externalId;
            this.timestamp = timestamp;
            this.locked = locked;
            this.srcLanguage = srcLanguage;
            this.hash = hash;
            this.dir = dir;
            dictionaries = new ArrayList<>();
        }
    }

    private class ImportSpecDictionary {

        public final String isoCode;
        public final String language;
        public final List<ImportSpecCard> cards;

        public ImportSpecDictionary(String isoCode, String language) {
            this.isoCode = isoCode;
            this.language = language;
            cards = new ArrayList<>();
        }
    }

    private class ImportSpecCard {

        public final String label;
        public final String filename;
        public final String translatedText;


        public ImportSpecCard(String label, String filename, String translatedText) {
            this.label = label;
            this.filename = filename;
            this.translatedText = translatedText;
        }
    }
}
