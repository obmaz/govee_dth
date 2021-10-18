/**
 *  Govee DTH
 *
 * MIT License
 *
 * Copyright (c) 2021 zambobmaz@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import groovy.json.JsonSlurper

metadata {
    definition(name: "Govee DTH", namespace: "imageafter45121", author: "obmaz", mnmn: "SmartThingsCommunity", vid: "57a6350a-9a7b-3195-949f-7e6abd35c804", ocfDeviceType: 'oic.d.light') {
        capability "Switch"
        capability "Switch Level"
        capability "Color Control"
        capability "Color Temperature"
        capability "Refresh"

		attribute "lastCheckin", "Date"
    }

    preferences {
        input name: "language", title: "Select a language", type: "enum", required: true, options: ["EN", "KR"], defaultValue: "EN", description: "Language for DTH"
    }
}

def updateLastTime() {
    log.debug "updateLastTime"
    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    sendEvent(name: "lastCheckin", value: now, displayed: false)
}

def installed() {
    log.debug "installed"
}

def updated() {
    log.debug "updated"
    initialize()
}

def initialize() {
    log.debug "initialize"
    refresh()
    // runEvery1Minute(refresh) // it can be cause http exception due to calling collision
    runEvery30Minute(refresh) // Hour : runEvery1Hour(), runEvery3Hour()
}

def refresh() {
    log.debug "refresh"
    def query = [device: deviceMac, model: deviceModel]
    def state = sendCommand1("GET", "/v1/devices/state", query, null)
    updateAttribute(state)
}

// Switch
def on() {
    log.debug "on"
    getDeviceMac
    def payload = [device: deviceMac, model: deviceModel, cmd: ["name": "turn", "value": "on"]]
    sendCommand("PUT", "/v1/devices/control", null, payload)
}

def off() {
    log.debug "off"
    def payload = [device: deviceMac, model: deviceModel, cmd: ["name": "turn", "value": "off"]]
    sendCommand("PUT", "/v1/devices/control", null, payload)
}

// Switch Level
def setLevel(level) {
    log.debug "setLevel level: $level"
    def payload = [device: deviceMac, model: deviceModel, cmd: ["name": "brightness", "value": level]]
    sendCommand("PUT", "/v1/devices/control", null, payload)
}

// Color Control
def setColor(color) { //map
    log.debug "setColor color: $color"
    def hex = colorUtil.hsvToHex(Math.round(color.hue) as int, Math.round(color.saturation) as int)
    def rgb = colorUtil.hexToRgb(hex)
    def payload = [device: deviceMac, model: deviceModel, cmd: ["name": "color", "value": ["r": rgb[0], "g": rgb[1], "b": rgb[2]]]]
    sendCommand("PUT", "/v1/devices/control", null, payload)
}

def setHue(hue) {
    log.debug "setHue hue: $hue"
}

def setSaturation(saturation) {
    log.debug "setSaturation saturation: $saturation"
}

// Color Temperature
def setColorTemperature(temperature) {
    log.debug "setColorTemperature temperature: $temperature"
    def payload = [device: deviceMac, model: deviceModel, cmd: ["name": "colorTem", "value": temperature]]
    sendCommand("PUT", "/v1/devices/control", null, payload)
}

def getApiKey() {
    return parent.getApiKey()
}

def getDeviceName() {
    return parent.getDeviceName()
}

def getDeviceMac() {
	return parent.getDeviceMac()
}

def getDeviceModel() {
    return parent.getDeviceModel()
}

def sendCommand(method, path, query, payload) {
    sendCommand1(method, path, query, payload)
    refresh()
}

def sendCommand1(method, path, query, payload) {
    log.debug "sendCommand Method: $method, Path: $path, query: $query, payload: $payload"

    def retVal
    def params = [
            method : method,
            uri    : "https://developer-api.govee.com",
            path   : path,
            headers: [
                    "Govee-API-Key": apiKey,
                    "Content-Type" : "application/json",
            ],
            query  : query,
            body   : payload
    ]

    try {
        if (params.method == 'GET') {
            httpGet(params) { resp ->
                retVal = resp.data
            }
        } else if (params.method == 'PUT') {
            httpPutJson(params) { resp ->
                retVal = resp.data
            }
        }
        log.debug "response.data : $retVal"
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "something went wrong: $e"
        retVal = 'error'
    }

    return retVal
}

def updateAttribute(body) {
    log.debug "updateAttribute: $body"

    if (body.code == 200 && body.data.device == deviceMac) {
        def bodyMap = [:]
        body.data.properties.each { item ->
            bodyMap << item
        }

        log.debug "bodyMap: ${bodyMap}"

        if (bodyMap.online != null) {
        }

        if (bodyMap.powerState != null) {
            sendEvent(name: "switch", value: bodyMap.powerState)
        }

        if (bodyMap.brightness != null) {
            sendEvent(name: "level", value: bodyMap.brightness)
        }

        if (bodyMap.color != null) {
            def hex = colorUtil.rgbToHex(bodyMap.color.r, bodyMap.color.g, bodyMap.color.b)
            def hsv = colorUtil.hexToHsv(hex) // hue, saturation, value
            sendEvent(name: "hue", value: hsv[0])
            sendEvent(name: "saturation", value: hsv[1])
        }
        
        if (bodyMap.colorTem != null) {
            sendEvent(name: "colorTemperature", value: bodyMap.colorTem, unit: "K")
		}
        
        if (bodyMap.colorTemInKelvin != null) {
            sendEvent(name: "colorTemperature", value: bodyMap.colorTemInKelvin, unit: "K")
		}
        updateLastTime()
    }
}