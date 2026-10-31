export function createDocumentAttempt(getEpoch,makeKey=()=>crypto.randomUUID()){
 let payload=null,busy=false,version=0;
 return {
  get payload(){return payload;},get busy(){return busy;},
  prepare(value){if(!payload)payload=Object.freeze({...structuredClone(value),requestKey:makeKey()});return payload;},
  reset(){payload=null;busy=false;version++;},
  async submit(send){if(busy||!payload)return null;const epoch=getEpoch(),ownVersion=version;busy=true;
   try{const result=await send(payload);if(epoch!==getEpoch()||version!==ownVersion)return null;payload=null;return result;}
   finally{if(epoch===getEpoch()&&version===ownVersion)busy=false;}
  }
 };
}
