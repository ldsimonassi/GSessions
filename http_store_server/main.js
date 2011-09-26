var rootDir= './files/'
var fs = require('fs');
var http = require('http');

function getFileName(id) {
	return rootDir+id+'.json';
}

function saveObject(id, fields){
	var fileName= getFileName(id)
	console.log("Writing ["+fileName+"] with:"+fields);
	fs.writeFileSync(fileName, fields, 'utf8')
}

function readObject(id){
	var fileName= getFileName(id)
	console.log("Reading ["+fileName+"]");
	return fs.readFileSync(fileName, 'utf8');
}

//Create the http server
http.createServer(function (request, response) {
	var re= new RegExp('/(.*).json$');
	var m= re.exec(request.url)
	var id= m[1]

	if(id=='newSessionId' && request.method=='GET'){
		var readedId= readObject(id);
		var lastId= parseInt(readedId);
		lastId++;
		saveObject(id, ''+lastId);
		response.writeHead(200, {
		  'Content-Type'  : 'application/json; charset=utf-8'
		});
		var content= '{"id":'+lastId+'}'
		response.end(content);
	} else if(id=='sync' && request.method=='POST') {
		request.content= "";
		
		request.addListener('data', function(chunk) {
			request.content += chunk;
		});
 
		response.writeHead(200, {
			'Content-Type'  : 'application/json; charset=utf-8',
		});

		request.addListener('end', function() {
			var data= JSON.parse(request.content);
			var content= JSON.stringify({"status":"OK"})
			var ret= new Object();

			if(data.save){
				ret['saved']= new Object()
			
				for(var id in data.save) {
					var object= data.save[id];
					saveObject(id, JSON.stringify(object));
					ret['saved'][id]= 'Ok';
				}

			}
			if(data.refresh) {
				ret['refreshed']= new Object();
				var fresh= ret['refreshed'];

				for(var id in data.refresh) {
					var o= JSON.parse(readObject(id));
					fresh[id]= o
				}
			}

			response.end(JSON.stringify(ret));
		});

	} else if(request.method=='GET'){
		// ANSWER TO A REGULAR GET
		var body= readObject(id);


		response.writeHead(200, {
		  'Content-Length': body.length,
		  'Content-Type'  : 'application/json; charset=utf-8'
		});
		response.end(body)
	} else if(request.method=='POST') {
		// ANSWER TO A REGULAR WRITE
		request.content= ""
		
		request.addListener('data', function(chunk) {
			request.content += chunk;
		});
 
		response.writeHead(200, {
		  'Content-Type'  : 'application/json; charset=utf-8',
		});

		request.addListener('end', function() {
			saveObject(id, request.content)
			var content= '{"stored":"OK"}';
			response.end(content);
		});

	} else {
		var body= '{"description":"Bad request!"}'
		response.writeHead(401, {
          'Content-Length': body.length,
          'Content-Type'  : 'application/json; charset=utf-8'
        });
        response.end(body)
	}
}).listen(8081, "localhost");
