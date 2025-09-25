package com.example.bluechat.Uicompose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun chatscreen(
    status: String,
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
){
    Column (modifier=Modifier
        .fillMaxSize()
        .padding(16.dp)){
        Text(text = status,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxSize())
    }
    Spacer(modifier = Modifier.height(16.dp))

    TextField(
        value = message,
        onValueChange = onMessageChange,
        placeholder = {Text(text = "enter message")}
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onSendClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = "send")


    }

}