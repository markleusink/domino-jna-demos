//disable the Dojo AMD loader
if (typeof define === 'function' && define.amd) {
	if(define.amd.vendor =='dojotoolkit.org'){
		define._amd = define.amd;
		delete define.amd;
	}
}