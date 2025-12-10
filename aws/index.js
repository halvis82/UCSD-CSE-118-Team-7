const Alexa = require('ask-sdk-core');
const AWS = require('aws-sdk');

const STS = new AWS.STS({ apiVersion: '2011-06-15' });

const DYNAMODB_TABLE_NAME = 'ContextMusicData'; 
const TARGET_DDB_REGION = 'us-west-1'; 
const CROSS_ACCOUNT_ROLE_ARN = 'arn:aws:iam::684212263961:role/AlexaDynamoRole'; 

const DDB_PARTITION_KEY_VALUE = 'MY_ALEXA_USER';

const Util = require('./util'); 

const CLASSIFICATION_MAPPING = {
    'SLEEPING': 'sleeping', 
    'ACTIVE': 'active_2',   
    'RESTING': 'resting',    
    'WORKOUT': 'workout', 
    'DEFAULT': 'active_2'
};

const TRACK_COUNTS = {
    'active_2': 5,
    'resting': 3,
    'sleeping': 11,
    'workout': 2
};

async function getAuthorizedDDBClient() {
    try {
        const assumedRole = await STS.assumeRole({
            RoleArn: CROSS_ACCOUNT_ROLE_ARN,
            RoleSessionName: 'AlexaContextMusicSession'
        }).promise();

        return new AWS.DynamoDB.DocumentClient({
            accessKeyId: assumedRole.Credentials.AccessKeyId,
            secretAccessKey: assumedRole.Credentials.SecretAccessKey,
            sessionToken: assumedRole.Credentials.SessionToken,
            region: TARGET_DDB_REGION
        });
    } catch (err) {
        console.error("STS Error:", err);
        throw err;
    }
}

const PlayContextMusicIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'PlayContextMusicIntent';
    },
    async handle(handlerInput) {
        let classificationKey = 'DEFAULT'; 
        let speechOutput = 'Starting context music.';

        try {
            const ddbClient = await getAuthorizedDDBClient();
            const params = {
                TableName: DYNAMODB_TABLE_NAME,
                Key: { 'UserID': DDB_PARTITION_KEY_VALUE }
            };
            const data = await ddbClient.get(params).promise();
            
            console.log("START: DynamoDB Response:", JSON.stringify(data));

            if (data.Item && data.Item.Movement) {
                const userClassification = data.Item.Movement.toUpperCase();
                if (CLASSIFICATION_MAPPING.hasOwnProperty(userClassification)) {
                    classificationKey = userClassification;
                }
                speechOutput = `Playing ${classificationKey.toLowerCase()} flow.`;
            } else {
                speechOutput = "Context not found. Playing default flow.";
            }

            const baseFileName = CLASSIFICATION_MAPPING[classificationKey];
            
            const startFileKey = `stream_assets/${baseFileName}_000.mp3`;
            const audioUrl = Util.getS3PreSignedUrl(`Media/${startFileKey}`);
            
            const streamToken = `${classificationKey}_0_${Date.now()}`; 

            console.log(`START: Playing URL: ${audioUrl}`);
            console.log(`START: Token: ${streamToken}`);

            return handlerInput.responseBuilder
                .speak(speechOutput)
                .addAudioPlayerPlayDirective('REPLACE_ALL', audioUrl, streamToken, 0, null)
                .withShouldEndSession(true)
                .getResponse();

        } catch (error) {
            console.error('Start Handler Error:', error);
            return handlerInput.responseBuilder
                .speak('Sorry, I encountered a system error starting the stream.')
                .withShouldEndSession(true)
                .getResponse();
        }
    }
};

const PlaybackNearlyFinishedHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'AudioPlayer.PlaybackNearlyFinished';
    },
    async handle(handlerInput) {
        try {
            console.log("QUEUE: Handler Triggered.");

            const currentToken = handlerInput.requestEnvelope.request.token;
            console.log(`QUEUE: Current Token: ${currentToken}`);

            const parts = currentToken.split('_');
            const currentMood = parts[0];
            const currentIndex = parseInt(parts[1], 10);

            const ddbClient = await getAuthorizedDDBClient();
            const params = {
                TableName: DYNAMODB_TABLE_NAME,
                Key: { 'UserID': DDB_PARTITION_KEY_VALUE }
            };
            const data = await ddbClient.get(params).promise();
            
            let nextMood = currentMood;
            let nextIndex = currentIndex + 1; 
            if (data.Item && data.Item.Movement) {
                const liveMood = data.Item.Movement.toUpperCase();
                if (CLASSIFICATION_MAPPING.hasOwnProperty(liveMood) && liveMood !== currentMood) {
                    console.log(`QUEUE: Context Switch Detected: ${currentMood} -> ${liveMood}`);
                    nextMood = liveMood;
                    nextIndex = 0;
                }
            }

            const nextBaseName = CLASSIFICATION_MAPPING[nextMood];
            const limit = TRACK_COUNTS[nextBaseName] || 1; 

            if (nextIndex >= limit) {
                console.log(`QUEUE: End of Track ${nextBaseName} (Index ${nextIndex} >= ${limit}). Looping to 0.`);
                nextIndex = 0; 
            }

            const nextIndexStr = nextIndex.toString().padStart(3, '0');
            const nextFileKey = `stream_assets/${nextBaseName}_${nextIndexStr}.mp3`;
            
            console.log(`QUEUE: Generating URL for key: Media/${nextFileKey}`);
            const nextUrl = Util.getS3PreSignedUrl(`Media/${nextFileKey}`);
            
            const nextToken = `${nextMood}_${nextIndex}_${Date.now()}`;

            return handlerInput.responseBuilder
                .addAudioPlayerPlayDirective(
                    'ENQUEUE', 
                    nextUrl, 
                    nextToken, 
                    0, 
                    currentToken 
                )
                .getResponse();

        } catch (error) {
            console.error('Queue Handler Error:', error);
            return handlerInput.responseBuilder.getResponse();
        }
    }
};

const PauseAndStopIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && (Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.PauseIntent'
            || Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.StopIntent'
            || Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.CancelIntent');
    },
    handle(handlerInput) {
        return handlerInput.responseBuilder
            .speak('Paused.')
            .addAudioPlayerStopDirective()
            .withShouldEndSession(true)
            .getResponse();
    }
};

const PlaybackFailedHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'AudioPlayer.PlaybackFailed';
    },
    handle(handlerInput) {
        console.error(`Playback Failed: ${JSON.stringify(handlerInput.requestEnvelope.request.error)}`);
        return handlerInput.responseBuilder.getResponse();
    }
};

const LaunchRequestHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'LaunchRequest';
    },
    handle(handlerInput) {
        return handlerInput.responseBuilder
            .speak('Context Music Ready.')
            .reprompt('Say play music.')
            .getResponse();
    }
};

const HelpIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.HelpIntent';
    },
    handle(handlerInput) {
        return handlerInput.responseBuilder.speak('Say play music.').reprompt('Say play music.').getResponse();
    }
};

const FallbackIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.FallbackIntent';
    },
    handle(handlerInput) {
        return handlerInput.responseBuilder.speak('Sorry, I don\'t understand.').reprompt('Say play music.').getResponse();
    }
};

const SessionEndedRequestHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'SessionEndedRequest';
    },
    handle(handlerInput) {
        console.log(`Session Ended: ${handlerInput.requestEnvelope.request.reason}`);
        if (handlerInput.requestEnvelope.request.error) {
            console.log(`Error Info: ${JSON.stringify(handlerInput.requestEnvelope.request.error)}`);
        }
        return handlerInput.responseBuilder.getResponse();
    }
};

const ErrorHandler = {
    canHandle() { return true; },
    handle(handlerInput, error) {
        console.error(`Global Error: ${error.message}`);
        return handlerInput.responseBuilder.speak('Error occurred.').getResponse();
    }
};

exports.handler = Alexa.SkillBuilders.custom()
    .addRequestHandlers(
        LaunchRequestHandler,
        PlayContextMusicIntentHandler,
        PlaybackNearlyFinishedHandler,
        PauseAndStopIntentHandler,
        PlaybackFailedHandler,
        HelpIntentHandler,
        FallbackIntentHandler,
        SessionEndedRequestHandler
    )
    .addErrorHandlers(ErrorHandler)
    .lambda();
